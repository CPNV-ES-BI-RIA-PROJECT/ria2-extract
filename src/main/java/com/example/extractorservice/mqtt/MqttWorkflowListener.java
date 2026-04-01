package com.example.extractorservice.mqtt;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.extractorservice.exception.BucketObjectNotFoundException;
import com.example.extractorservice.exception.BucketOperationException;
import com.example.extractorservice.service.ExtractorService;
import com.example.extractorservice.service.WorkflowExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true")
public class MqttWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(MqttWorkflowListener.class);
    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";
    private static final long RECONNECT_DELAY_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final ExtractorService extractorService;
    private final MqttProperties mqttProperties;
    private final TaskExecutor taskExecutor;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(
            new MqttReconnectThreadFactory());

    private MqttClient client;

    public MqttWorkflowListener(
            ObjectMapper objectMapper,
            ExtractorService extractorService,
            MqttProperties mqttProperties,
            @Qualifier("mqttTaskExecutor") TaskExecutor taskExecutor) {
        this.objectMapper = objectMapper;
        this.extractorService = extractorService;
        this.mqttProperties = mqttProperties;
        this.taskExecutor = taskExecutor;
    }

    @PostConstruct
    void start() {
        mqttProperties.validate();

        try {
            client = new MqttClient(
                    mqttProperties.resolvedBrokerUrl(),
                    resolveClientId());
            client.setCallback(new WorkflowMqttCallback());
        } catch (MqttException e) {
            throw new IllegalStateException("Failed to initialize MQTT client", e);
        }

        reconnectExecutor.scheduleWithFixedDelay(this::connectIfNeeded, 0, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        reconnectExecutor.shutdownNow();

        if (client == null) {
            return;
        }

        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException e) {
            log.warn("Failed to close MQTT client cleanly", e);
        }
    }

    private void connectIfNeeded() {
        if (client == null || client.isConnected()) {
            return;
        }

        try {
            client.connect(buildConnectOptions());
            log.info("MQTT listener connected to {} as {}", mqttProperties.getBrokerUrl(), client.getClientId());
        } catch (MqttException e) {
            log.warn(
                    "Unable to connect to MQTT broker {}. Retrying in {} seconds.",
                    mqttProperties.getBrokerUrl(),
                    RECONNECT_DELAY_SECONDS);
            log.debug("MQTT connection attempt failed", e);
        }
    }

    private MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        if (StringUtils.hasText(mqttProperties.getUsername())) {
            options.setUserName(mqttProperties.getUsername().trim());
        }
        if (mqttProperties.getPassword() != null) {
            options.setPassword(mqttProperties.getPassword().toCharArray());
        }

        return options;
    }

    private String resolveClientId() {
        if (StringUtils.hasText(mqttProperties.getClientId())) {
            return mqttProperties.getClientId().trim();
        }

        return mqttProperties.getServiceName().trim() + "-" + UUID.randomUUID();
    }

    private void subscribeToStartTopic() throws MqttException {
        client.subscribe(mqttProperties.startCommandTopic(), mqttProperties.getQos());
        log.info("Subscribed to MQTT topic {}", mqttProperties.startCommandTopic());
    }

    private void handleStartCommand(byte[] payload) {
        JsonNode command;

        try {
            command = objectMapper.readTree(payload);
        } catch (IOException e) {
            log.warn("Ignoring MQTT start command with invalid JSON payload", e);
            return;
        }

        String jobId = textValue(command, "job_id");
        String schemaVersion = textValue(command, "schemaVersion");
        String sourceUri = textValue(command.path("input"), "uri");

        if (!StringUtils.hasText(jobId)) {
            log.warn("Ignoring MQTT start command without job_id");
            return;
        }

        if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
            publishFailure(jobId, "INVALID_SCHEMA_VERSION",
                    "Unsupported schemaVersion: " + (schemaVersion == null ? "<missing>" : schemaVersion));
            return;
        }

        if (!StringUtils.hasText(sourceUri)) {
            publishFailure(jobId, "INVALID_COMMAND", "input.uri is required");
            return;
        }

        taskExecutor.execute(() -> processWorkflow(jobId, sourceUri));
    }

    private void processWorkflow(String jobId, String sourceUri) {
        publishRunning(jobId);

        try {
            WorkflowExecutionResult result = extractorService.executeWorkflow(sourceUri, jobId);
            publishCompleted(jobId, result);
        } catch (Exception e) {
            publishFailure(jobId, errorCodeFor(e), errorMessageFor(e));
        }
    }

    private void publishRunning(String jobId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("job_id", jobId);
        payload.put("progress", 0);

        publish(mqttProperties.runningEventTopic(), payload);
    }

    private void publishCompleted(String jobId, WorkflowExecutionResult result) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("durationMs", result.durationMs());
        stats.put("bytesProcessed", result.contentLength());

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("uri", result.sharedUrl());
        output.put("destination", result.destinationRemote());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("job_id", jobId);
        payload.put("output", output);
        payload.put("stats", stats);

        publish(mqttProperties.completedEventTopic(), payload);
    }

    private void publishFailure(String jobId, String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("job_id", jobId);
        payload.put("error", error);

        publish(mqttProperties.failedEventTopic(), payload);
    }

    private void publish(String topic, Map<String, Object> payload) {
        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("MQTT client is not connected");
        }

        try {
            MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(payload));
            message.setQos(mqttProperties.getQos());
            message.setRetained(false);
            client.publish(topic, message);
        } catch (MqttException | IOException e) {
            throw new IllegalStateException("Failed to publish MQTT message to topic " + topic, e);
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        return field.asText();
    }

    private String errorCodeFor(Exception exception) {
        if (exception instanceof BucketObjectNotFoundException) {
            return "SOURCE_NOT_FOUND";
        }
        if (exception instanceof IllegalArgumentException) {
            return "INVALID_INPUT";
        }
        if (exception instanceof IllegalStateException) {
            return "CONFIGURATION_ERROR";
        }
        if (exception instanceof BucketOperationException) {
            return "WORKFLOW_OPERATION_ERROR";
        }
        return "WORKFLOW_FAILED";
    }

    private String errorMessageFor(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private final class WorkflowMqttCallback implements MqttCallbackExtended {

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            try {
                subscribeToStartTopic();
            } catch (MqttException e) {
                throw new IllegalStateException("Failed to subscribe to MQTT start topic", e);
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            log.warn("MQTT connection lost", cause);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            log.debug("Received MQTT message on topic {}", topic);
            handleStartCommand(message.getPayload());
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            log.debug("MQTT delivery completed for token {}", token.getMessageId());
        }
    }

    private static final class MqttReconnectThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "mqtt-reconnect");
            thread.setDaemon(true);
            return thread;
        }
    }
}
