# ETL MQTT Communication Contract v0.2

```mermaid
sequenceDiagram
    title ETL MQTT Communication Contract v0.2
    participant Orchestrator
    participant Broker as MQTT Broker
    participant Extract as Extract Service
    participant Transform as Transform Service
    participant Load as Load Service

    Note over Orchestrator,Broker: Topics follow etl/{namespace}/{service}/{command_type}/{action}.<br/>Payloads are JSON and must include schemaVersion for versioned parsing.<br/>Orchestrator drives cmd/... topics; services emit event/{status}.

    Orchestrator->>Broker: Subscribe etl/{ns}/*/event/status
    Broker-->>Orchestrator: Subscription active
    Extract->>Broker: Subscribe etl/{ns}/extract/cmd/*
    Broker-->>Extract: Subscription active
    Transform->>Broker: Subscribe etl/{ns}/transform/cmd/*
    Broker-->>Transform: Subscription active
    Load->>Broker: Subscribe etl/{ns}/load/cmd/*
    Broker-->>Load: Subscription active

    Orchestrator->>Broker: Publish etl/{ns}/extract/cmd/start\n(job_id,input.uri,options?)
    Broker->>Extract: Deliver start command
    Note right of Extract: Send status when starting, completing,<br/>and on failures. Periodic updates optional.
    Extract->>Broker: Publish etl/{ns}/extract/event/running\nprogress?, stats?
    Broker->>Orchestrator: Forward status update
    Extract->>Broker: Publish etl/{ns}/extract/event/completed\ninclude output.uri
    Extract->>Broker: Publish etl/{ns}/extract/event/failed\ninclude error.code/message
    Broker->>Orchestrator: Forward completion / failure

    Orchestrator->>Broker: Publish etl/{ns}/transform/cmd/start
    Broker->>Transform: Deliver start command
    Transform->>Broker: Publish event/running, event/completed,\nevent/failed topics as above
    Broker->>Orchestrator: Forward status updates

    Orchestrator->>Broker: Publish etl/{ns}/load/cmd/start
    Broker->>Load: Deliver start command
    Load->>Broker: Publish event/running, event/completed,\nevent/failed topics as above
    Broker->>Orchestrator: Forward status updates
```
