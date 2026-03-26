package com.example.extractorservice.service;

public record WorkflowExecutionResult(
        String destinationRemote,
        String sharedUrl,
        long durationMs,
        int contentLength) {
}
