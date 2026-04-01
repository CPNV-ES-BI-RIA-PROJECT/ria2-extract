package com.example.extractorservice.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    private boolean enabled;
    private String brokerUrl;
    private String namespace = "stack1";
    private String serviceName = "extract";
    private String clientId;
    private String username;
    private String password;
    private int qos = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public void validate() {
        if (!enabled) {
            return;
        }

        if (brokerUrl == null || brokerUrl.isBlank()) {
            throw new IllegalStateException(
                    "mqtt.broker-url is required when MQTT is enabled.\n"
                            + "When running locally: Add to .env file as MQTT_BROKER_URL=value\n"
                            + "When running in Docker: Set environment variable MQTT_BROKER_URL");
        }
    }

    public String resolvedBrokerUrl() {
        if (brokerUrl == null) {
            return null;
        }

        String normalizedBrokerUrl = brokerUrl.trim();

        if (normalizedBrokerUrl.startsWith("mqtt://")) {
            return "tcp://" + normalizedBrokerUrl.substring("mqtt://".length());
        }

        if (normalizedBrokerUrl.startsWith("mqtts://")) {
            return "ssl://" + normalizedBrokerUrl.substring("mqtts://".length());
        }

        return normalizedBrokerUrl;
    }

    public String startCommandTopic() {
        return topic("cmd", "start");
    }

    public String runningEventTopic() {
        return topic("event", "running");
    }

    public String completedEventTopic() {
        return topic("event", "completed");
    }

    public String failedEventTopic() {
        return topic("event", "failed");
    }

    private String topic(String commandType, String action) {
        return "etl/%s/%s/%s/%s".formatted(namespace.trim(), serviceName.trim(), commandType, action);
    }
}
