# ETL MQTT Communication Contract
Version `0.2` - Last Updated: `26-02-2026`

## 1. General Principles
 - All topics will follow a consistent naming convention to ensure clarity and organization. The general format for topic names will be:

    ```
    etl/{namespace}/{service_name}/{command_type}/{action}
    ```
    Where:
    - `{namespace}` is a grouping of the stack, in case a single broker would be shared between multiple stacks. For example, we could use `stack1` for our current stack.
    - `{service_name}` is the name of the ETL service (e.g., `extract`, `transform`, `load`).
    - `{command_type}` indicates the type of command being sent (e.g., `cmd` or `event`).
    - `{action}` is the specific action being performed or the status being reported.

 - Messages will be structured in JSON format to ensure readability and ease of parsing across different programming languages.
 - Every messages **MUST** include a `schemaVersion` field to indicate the version of the message schema being used. This will allow us to manage changes to the message formats over time and ensure compatibility between different versions of the Orchestrator and ETL services.
 - The Orchestrator will be responsible for publishing messages to command the ETL services, while the ETL services will publish messages to report their status back to the Orchestrator.

## 2. Message Formats
### 2.1. Orchestrator to ETL Services
Orchestrator will publish messages to the following topics to communicate with the ETL services:
- `etl/{namespace}/{service_name}/cmd/start`: To order the start of a specific ETL process.
    - Payload Example:
    ```json
    {
        <!-- mandatory fields -->
        "schemaVersion": "1.0",
        "job_id": "12345",
        "input": {
            "uri": "https://amazon.com/shared_file.csv",
        }
        <!-- optional fields -->
        "options": {
            "param1": "value1",
            "param2": "value2"
        }
    }
    ```
    - `job_id`: **Required parameter** A unique identifier for the job.
    - `input`: **Required parameter** A dictionary containing any necessary input parameters for the ETL process.
    - `uri`: **Required parameter** The URL to download the data.
    - `options`: An optional dictionary containing any additional parameters or configurations for the ETL process. The specific options will depend on the requirements of the ETL service being commanded. We could use `dryRun`,`timeoutSeconds`,`priority` or any other relevant parameters that we identify as we clarify the needs of the ETL processes.
- other commands can be added later as we clarify the needs. Here are some examples of other commands we might need:
    - `etl/{namespace}/{service_name}/cmd/stop`: To signal the stop of a specific ETL process. (by passing the `job_id` in the payload to identify which process to stop)
    - `etl/{namespace}/{service_name}/cmd/status`: To request the status of a specific ETL process. (by passing the `job_id` in the payload to identify which process we want the status of)
### 2.2. ETL Services to Orchestrator (and any other subscribers interested in the status of the ETL services, such as a monitoring service)
ETL services will publish messages to the following topics to communicate back to the Orchestrator:
- `etl/{namespace}/{service_name}/event/running`: Emitted whenever the service starts a job or posts a progress update.
- `etl/{namespace}/{service_name}/event/completed`: Emitted when the job finishes successfully.
- `etl/{namespace}/{service_name}/event/failed`: Emitted when the job fails.

- The service **must** send a running update at least when starting the job, a completed message on success, and a failed message if an error occurs. It **may** also send periodic running updates during the execution of the job.
- (Not to implement in first sprint) In case the orchestrator requests a status update (via `etl/{namespace}/{service_name}/cmd/status`), the service **must** send a message on the corresponding `event/{status}` topic in response to that request, even if there is no change in the job status since the last update. This is to ensure that the orchestrator receives a response to its request and can confirm that the service is still responsive and working on that job.
- Payload Example (running):
    ```json
    {
        <!-- mandatory fields -->
        "schemaVersion": "1.0",
        "job_id": "12345",
        <!-- optional fields -->
        "progress": 50,
        "stats": {
            "items_processed": 10000,
            "durationMs": 120345
        }
    }
    ```
    - Payload Example (completed):
    ```json
    {
        <!-- mandatory fields -->
        "schemaVersion": "1.0",
        "job_id": "12345",
        "output": {
            "uri": "https://amazon.com/shared_data.csv"
        }
    }
    ```
    - Payload Example (failed):
    ```json
    {
        <!-- mandatory fields -->
        "schemaVersion": "1.0",
        "job_id": "12345",
        "error": {
            "code": "TRANSFORM_VALIDATION_ERROR", 
            "message": "Column 'price' contains invalid values"
        }
    }
    ```
    - `job_id`: **Required parameter** The unique identifier for the job (matching the one sent by the Orchestrator).
    - `progress`: An optional field indicating the progress percentage of the job (used on `event/running` messages).
    - `output`: A mandatory field for `event/completed` (except for load if not applicable) containing any output parameters and an URI from the ETL process.
    - `stats`: An optional field containing any relevant statistics about the job execution (e.g., number of items processed, duration, etc.). This data is not mandatory, but it can be very useful for monitoring and debugging purposes.
    - `error`: A mandatory field for `event/failed` containing details about the error that occurred during the job execution. It should include an error `code` and a human-readable `message` describing the error.
## 3. Communication Flow
This describe the communication sequence for a typical ETL job execution:
 - All services subscribe to their relevent topics.
   - The Orchestrator subscribes to `etl/{namespace}/*/event/+` (or each individual status topic) to receive status updates from the ETL services.
   - Each ETL service subscribes to its command topics (e.g., `etl/{namespace}/extract/cmd/*`.
 - Orchestrator publishes `etl/{namespace}/extract/cmd/start` with the necessary payload to start an extract job.
 - Orchestrator waits for `etl/{namespace}/extract/event/completed` with the matching `job_id` to receive the completion message (or `event/failed` to handle failures).
 - Orchestrator publishes `etl/{namespace}/transform/cmd/start` with the necessary payload to start a transform job.
 - Orchestrator waits for `etl/{namespace}/transform/event/completed` with the matching `job_id` to receive the completion message.
 - Orchestrator publishes `etl/{namespace}/load/cmd/start` with the necessary payload to start a load job.
 - Orchestrator waits for `etl/{namespace}/load/event/completed` with the matching `job_id` to receive the completion message.

Look at the sequence diagram in `communication_contract_sequence.puml` for a visual representation of this communication flow.