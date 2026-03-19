package com.example.extractorservice.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request payload for the extract workflow")
public record ExtractWorkflowRequest(
        @Schema(description = "Public or pre-signed source URL", example = "https://public.example.com/calendar.ics", requiredMode = Schema.RequiredMode.REQUIRED) String url) {
}
