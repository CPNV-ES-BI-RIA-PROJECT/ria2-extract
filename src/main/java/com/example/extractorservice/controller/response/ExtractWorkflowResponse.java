package com.example.extractorservice.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response payload for the extract workflow")
public record ExtractWorkflowResponse(
        @Schema(description = "Shared URL generated for the uploaded file", example = "https://bucket.example.com/calendar.ics?signature=xyz", requiredMode = Schema.RequiredMode.REQUIRED) String url) {
}
