# MQTT SDK Guide - Spring Java

This guide is aligned with `COMMUNICATION_CONTRACT.md`:
- Topic format: `etl/{namespace}/{service_name}/{command_type}/{action}`
- Service subscribes to `.../cmd/start`
- Service emits `.../event/running|completed|failed`
- `schemaVersion` is mandatory in all messages and will be equal to `1.0` for the initial version of the contract

## Recommended SDKs

1. `org.springframework.integration:spring-integration-mqtt` (best Spring-native integration)
2. `org.eclipse.paho:org.eclipse.paho.client.mqttv3` (simple direct MQTT client)

## Compact example (Paho)

```java
import com.fasterxml.jackson.databind.*;
import org.eclipse.paho.client.mqttv3.*;

ObjectMapper om = new ObjectMapper();
String ns = "stack1", svc = "extract", sv = "1.0";
String t(String type, String action) { return "etl/%s/%s/%s/%s".formatted(ns, svc, type, action); }

MqttClient c = new MqttClient("tcp://mosquitto:1883", svc + "-1");
c.connect();
c.subscribe(t("cmd", "start"), (topic, msg) -> {
  JsonNode p = om.readTree(msg.getPayload());
  if (!p.hasNonNull("schemaVersion") || !p.hasNonNull("job_id") || !p.path("input").hasNonNull("uri")) return;
  String job = p.get("job_id").asText();

  c.publish(t("event", "running"),   new MqttMessage(om.writeValueAsBytes(java.util.Map.of("schemaVersion", sv, "job_id", job, "progress", 0))));
  c.publish(t("event", "completed"), new MqttMessage(om.writeValueAsBytes(java.util.Map.of("schemaVersion", sv, "job_id", job, "output", java.util.Map.of("uri", "s3://bucket/out-" + job + ".csv")))));
  // on error: publish event/failed with { schemaVersion, job_id, error:{code,message} }
});
```

## References
https://docs.spring.io/spring-integration/reference/mqtt.html
https://github.com/eclipse-paho/paho.mqtt.java

## Team conventions to keep consistent

- Keep `job_id` unchanged across all lifecycle events.
- Emit at least one `running`, then either `completed` or `failed`.
- Validate `schemaVersion` and required command fields before starting work.
