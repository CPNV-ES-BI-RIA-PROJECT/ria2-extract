package com.example.extractorservice.service;

import static com.example.extractorservice.helper.ConfigHelper.getConfig;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.extractorservice.adapter.ExtractorAdapter;
import com.example.extractorservice.factory.ExtractorServiceFactory;

import jakarta.annotation.PostConstruct;

@Service
public class ExtractorService {

    private static final int DEFAULT_WORKFLOW_EXPIRATION_TIME = 3600;
    private static final String DEFAULT_WORKFLOW_FILE_NAME = "downloaded-file";
    private static final DateTimeFormatter WORKFLOW_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd-HH-mm-ss")
            .withZone(ZoneOffset.UTC);
    private static final String DESTINATION_BUCKET_ENV = "DESTINATION_BUCKET";

    private final ExtractorServiceFactory factory;
    private ExtractorAdapter adapter;

    public ExtractorService(ExtractorServiceFactory factory) {
        this.factory = factory;
    }

    @PostConstruct
    void init() {
        this.adapter = factory.getAdapter();
    }

    public void upload(String remote, byte[] content) {
        adapter.upload(remote, content);
    }

    public byte[] download(String remote) {
        return adapter.download(remote);
    }

    public void update(String remote, byte[] content) {
        adapter.update(remote, content);
    }

    public void delete(String remote, boolean recursive) {
        adapter.delete(remote, recursive);
    }

    public List<String> list(String remote) {
        return adapter.list(remote);
    }

    public String share(String remote, int expirationTime) {
        return adapter.share(remote, expirationTime);
    }

    public String executeWorkflow(String sourceUrl) {
        return executeWorkflow(sourceUrl, null).sharedUrl();
    }

    public WorkflowExecutionResult executeWorkflow(String sourceUrl, String workflowReference) {
        long startedAt = System.nanoTime();
        String destinationRemote = buildWorkflowDestinationRemote(sourceUrl, workflowReference);
        byte[] content = adapter.download(sourceUrl);
        adapter.upload(destinationRemote, content);
        String sharedUrl = adapter.share(destinationRemote, DEFAULT_WORKFLOW_EXPIRATION_TIME);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        return new WorkflowExecutionResult(destinationRemote, sharedUrl, durationMs, content.length);
    }

    private String buildWorkflowDestinationRemote(String sourceUrl, String workflowReference) {
        String normalizedBucket = normalizeWorkflowDestinationBucket();
        String filename = extractFilename(sourceUrl);
        String timestamp = WORKFLOW_TIMESTAMP_FORMATTER.format(Instant.now());
        String normalizedWorkflowReference = normalizeWorkflowReference(workflowReference);

        if (normalizedWorkflowReference.isBlank()) {
            return normalizedBucket + "/" + timestamp + "-" + filename;
        }

        return normalizedBucket + "/" + timestamp + "-" + normalizedWorkflowReference + "-" + filename;
    }

    private String normalizeWorkflowDestinationBucket() {
        String normalizedBucket = getConfig(
                DESTINATION_BUCKET_ENV,
                "Workflow destination bucket").trim()
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");

        if (normalizedBucket.isBlank()) {
            throw new IllegalStateException(
                    "Workflow destination bucket is not configured.\n"
                            + "When running locally: Add to .env file as DESTINATION_BUCKET=value\n"
                            + "When running in Docker: Set environment variable DESTINATION_BUCKET");
        }

        return normalizedBucket;
    }

    private String extractFilename(String sourceUrl) {
        URI sourceUri = URI.create(sourceUrl);
        String path = sourceUri.getPath();

        if (path == null || path.isBlank() || path.endsWith("/")) {
            return DEFAULT_WORKFLOW_FILE_NAME;
        }

        String filename = path.substring(path.lastIndexOf('/') + 1).trim();
        return filename.isEmpty() ? DEFAULT_WORKFLOW_FILE_NAME : filename;
    }

    private String normalizeWorkflowReference(String workflowReference) {
        if (workflowReference == null || workflowReference.isBlank()) {
            return "";
        }

        return workflowReference.trim()
                .replaceAll("[^a-zA-Z0-9._-]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }
}
