# MQTT Usage Guide

This document explains how to use the Extract service through MQTT.

The MQTT workflow is an additional transport layer on top of the existing extract workflow. The REST API remains available and unchanged.

## What the service does

When the service receives an MQTT start command, it:

1. reads the source file from `input.uri`
2. downloads the raw bytes from that URL
3. uploads the same bytes to `DESTINATION_BUCKET`
4. generates a new shared URL for the uploaded file
5. publishes lifecycle events back to MQTT

## Required configuration

The usual storage configuration is still required:

```bash
SPRING_APPLICATION_NAME=extractorservice
SERVER_PORT=8080
DESTINATION_BUCKET=my-destination-bucket
PROVIDER_IMPL=AWS
```

Add the MQTT configuration:

```bash
MQTT_ENABLED=true
MQTT_BROKER_URL=tcp://localhost:1883
MQTT_NAMESPACE=stack1
MQTT_SERVICE_NAME=extract
MQTT_QOS=1
```

Optional authentication values:

```bash
MQTT_CLIENT_ID=extract-service
MQTT_USERNAME=
MQTT_PASSWORD=
```

## Run the app with Maven

### 1. Start an MQTT broker

The app is an MQTT client. You need a broker running separately.

The quickest local option is Mosquitto with Docker:

```bash
docker run -d \
  --name extractor-mosquitto \
  -p 1883:1883 \
  eclipse-mosquitto:2
```

### 2. Configure `.env`

For a local Maven run, the app must connect to the broker through your host machine:

```bash
MQTT_ENABLED=true
MQTT_BROKER_URL=tcp://localhost:1883
MQTT_NAMESPACE=stack1
MQTT_SERVICE_NAME=extract
MQTT_QOS=1
```

Keep your usual storage configuration in the same `.env`.

### 3. Start the app

```bash
mvn spring-boot:run
```

If the broker is not yet available, the app still starts and retries the MQTT connection in the background every 10 seconds.

## Run the app with Docker Compose

The repository `docker-compose.yml` now includes:

- a `mosquitto` service listening on port `1883`
- the extract service configured to use `tcp://mosquitto:1883` inside the Docker network

### 1. Configure `.env`

You can keep a host-oriented MQTT URL for Maven usage:

```bash
MQTT_ENABLED=true
MQTT_BROKER_URL=tcp://localhost:1883
```

Docker Compose overrides the app container value automatically with:

```bash
MQTT_BROKER_URL=tcp://mosquitto:1883
```

You may optionally define:

```bash
MQTT_BROKER_URL_CONTAINER=tcp://mosquitto:1883
MQTT_HOST_PORT=1883
```

### 2. Start everything

```bash
docker compose up --build
```

This starts:

- `mosquitto` for MQTT
- `bucket-adapter` for the extractor service

### 3. Stop everything

```bash
docker compose down
```

## Topics used by the app

The app subscribes to:

```text
etl/{namespace}/{service_name}/cmd/start
```

With the default configuration:

```text
etl/stack1/extract/cmd/start
```

The app publishes lifecycle events to:

```text
etl/{namespace}/{service_name}/event/running
etl/{namespace}/{service_name}/event/completed
etl/{namespace}/{service_name}/event/failed
```

With the default configuration:

```text
etl/stack1/extract/event/running
etl/stack1/extract/event/completed
etl/stack1/extract/event/failed
```

## Start command payload

The service expects JSON with:

- `schemaVersion`: must be `"1.0"`
- `job_id`: unique identifier for the workflow
- `input.uri`: source public or pre-signed URL
- `options`: optional object, currently ignored by the app

Example:

```json
{
  "schemaVersion": "1.0",
  "job_id": "job-20260326-001",
  "input": {
    "uri": "https://public.example.com/calendar.ics"
  },
  "options": {
    "priority": "normal"
  }
}
```

## Published events

### Running

Sent when the job starts:

```json
{
  "schemaVersion": "1.0",
  "job_id": "job-20260326-001",
  "progress": 0
}
```

### Completed

Sent when the workflow succeeds:

```json
{
  "schemaVersion": "1.0",
  "job_id": "job-20260326-001",
  "output": {
    "uri": "https://signed.example.com/...",
    "destination": "my-destination-bucket/2026-03-26-09-15-33-job-20260326-001-calendar.ics"
  },
  "stats": {
    "durationMs": 742,
    "bytesProcessed": 18342
  }
}
```

Notes:

- `output.uri` is the generated shared URL for the uploaded file
- `output.destination` is an additional field exposed by this app to show where the file was stored

### Failed

Sent when the workflow cannot complete:

```json
{
  "schemaVersion": "1.0",
  "job_id": "job-20260326-001",
  "error": {
    "code": "SOURCE_NOT_FOUND",
    "message": "Object not found in bucket: https://public.example.com/missing.ics"
  }
}
```

Common error codes returned by the app:

- `INVALID_SCHEMA_VERSION`
- `INVALID_COMMAND`
- `INVALID_INPUT`
- `SOURCE_NOT_FOUND`
- `CONFIGURATION_ERROR`
- `WORKFLOW_OPERATION_ERROR`
- `WORKFLOW_FAILED`

## Local test with Mosquitto CLI

> Note : you have to install mosquitto package on your machine

Subscribe to all extract events:

```bash
mosquitto_sub -h localhost -p 1883 -t 'etl/stack1/extract/event/#' -v
```

Publish a start command:

```bash
mosquitto_pub -h localhost -p 1883 \
  -t 'etl/stack1/extract/cmd/start' \
  -m '{
    "schemaVersion":"1.0",
    "job_id":"job-20260326-001",
    "input":{
      "uri":"https://public.example.com/calendar.ics"
    }
  }'
```

Expected event flow:

1. one message on `etl/stack1/extract/event/running`
2. one message on `etl/stack1/extract/event/completed`

```bash
etl/stack1/extract/event/running {"schemaVersion":"1.0","job_id":"job-20260326-001","progress":0}

etl/stack1/extract/event/completed {"schemaVersion":"1.0","job_id":"job-20260326-001","output":{"uri":"https://bi1-david.s3.eu-west-1.amazonaws.com/2026-03-26-10-40-39-job-20260326-001-Capture-decran-2024-01-11-a-10.11.27-1.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20260326T104040Z&X-Amz-SignedHeaders=host&X-Amz-Credential=AKIA2KFJKL4OVJT46C4D%2F20260326%2Feu-west-1%2Fs3%2Faws4_request&X-Amz-Expires=3600&X-Amz-Signature=c3e983496d65682106988d2cb3a742ac7118f1e991e182fe8bf1a2764fceb18f","destination":"bi1-david/2026-03-26-10-40-39-job-20260326-001-Capture-decran-2024-01-11-a-10.11.27-1.png"},"stats":{"durationMs":937,"bytesProcessed":157967}}
```

If the workflow fails, the second message will be published on `etl/stack1/extract/event/failed` instead.

When the app runs through Docker Compose, you can still use these same commands from your host machine because Mosquitto is exposed on port `1883`.

## Behavior notes

- MQTT is optional. If `MQTT_ENABLED=false`, the app behaves like before and only REST is active.
- REST and MQTT can be used at the same time in the same application instance.
- The app processes MQTT jobs asynchronously, so the MQTT callback thread is not blocked by the file transfer.
- The destination object name includes a timestamp and the `job_id` when available to reduce collisions.
- If MQTT is enabled but the broker is temporarily unavailable at startup, the app still starts and retries the broker connection in the background every 10 seconds.
