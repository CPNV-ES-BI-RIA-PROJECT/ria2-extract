package com.example.extractorservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.extractorservice.adapter.ExtractorAdapter;
import com.example.extractorservice.factory.ExtractorServiceFactory;

@ExtendWith(MockitoExtension.class)
class ExtractorServiceWorkflowTest {
    private static final int DEFAULT_WORKFLOW_EXPIRATION_TIME = 3600;
    private static final String DESTINATION_BUCKET = "my-bucket";
    private static final String TIMESTAMPED_CALENDAR_PATTERN =
            "my-bucket/\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-calendar\\.ics";
    private static final String TIMESTAMPED_DEFAULT_FILE_PATTERN =
            "my-bucket/\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-downloaded-file";

    @Mock
    private ExtractorServiceFactory factory;

    @Mock
    private ExtractorAdapter adapter;

    private ExtractorService extractorService;

    @BeforeEach
    void setUp() {
        System.setProperty("DESTINATION_BUCKET", DESTINATION_BUCKET);
        when(factory.getAdapter()).thenReturn(adapter);
        extractorService = new ExtractorService(factory);
        extractorService.init();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("DESTINATION_BUCKET");
    }

    @Test
    void executeWorkflow_shouldDownloadUploadAndShare() {
        String sourceUrl = "https://public.example.com/calendar.ics?signature=abc";
        byte[] content = "BEGIN:VCALENDAR".getBytes();
        String sharedUrl = "https://bucket.example.com/calendar.ics?signature=xyz";

        when(adapter.download(sourceUrl)).thenReturn(content);
        when(adapter.share(
                argThat(remote -> remote != null
                        && remote.matches(TIMESTAMPED_CALENDAR_PATTERN)),
                eq(DEFAULT_WORKFLOW_EXPIRATION_TIME)))
                .thenReturn(sharedUrl);

        String result = extractorService.executeWorkflow(sourceUrl);

        InOrder inOrder = inOrder(adapter);
        inOrder.verify(adapter).download(sourceUrl);
        inOrder.verify(adapter).upload(
                argThat(remote -> remote != null
                        && remote.matches(TIMESTAMPED_CALENDAR_PATTERN)),
                eq(content));
        inOrder.verify(adapter).share(
                argThat(remote -> remote != null
                        && remote.matches(TIMESTAMPED_CALENDAR_PATTERN)),
                eq(DEFAULT_WORKFLOW_EXPIRATION_TIME));

        assertEquals(sharedUrl, result);
    }

    @Test
    void executeWorkflow_shouldFallbackToDefaultFileNameWhenUrlHasNoFileSegment() {
        String sourceUrl = "https://a-random-url.com";
        byte[] content = "payload".getBytes();
        String sharedUrl = "https://bucket.example.com/downloaded-file?signature=xyz";

        when(adapter.download(sourceUrl)).thenReturn(content);
        when(adapter.share(
                argThat(remote -> remote != null
                        && remote.matches(TIMESTAMPED_DEFAULT_FILE_PATTERN)),
                eq(DEFAULT_WORKFLOW_EXPIRATION_TIME)))
                .thenReturn(sharedUrl);

        String result = extractorService.executeWorkflow(sourceUrl);

        InOrder inOrder = inOrder(adapter);
        inOrder.verify(adapter).download(sourceUrl);
        inOrder.verify(adapter).upload(
                argThat(remote -> remote != null
                        && remote.matches(TIMESTAMPED_DEFAULT_FILE_PATTERN)),
                eq(content));
        inOrder.verify(adapter).share(
                argThat(remote -> remote != null
                        && remote.matches(TIMESTAMPED_DEFAULT_FILE_PATTERN)),
                eq(DEFAULT_WORKFLOW_EXPIRATION_TIME));

        assertEquals(sharedUrl, result);
    }

    @Test
    void executeWorkflow_shouldFailWhenDestinationBucketIsMissing() {
        System.clearProperty("DESTINATION_BUCKET");

        ExtractorService serviceWithoutBucket = new ExtractorService(factory);
        serviceWithoutBucket.init();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> serviceWithoutBucket.executeWorkflow("https://public.example.com/calendar.ics"));

        assertEquals(
                "Workflow destination bucket is not configured.\n"
                        + "When running locally: Add to .env file as DESTINATION_BUCKET=value\n"
                        + "When running in Docker: Set environment variable DESTINATION_BUCKET",
                exception.getMessage());
    }
}
