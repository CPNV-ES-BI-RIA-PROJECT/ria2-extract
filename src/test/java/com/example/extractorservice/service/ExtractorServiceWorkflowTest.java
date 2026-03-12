package com.example.extractorservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

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

    @Mock
    private ExtractorServiceFactory factory;

    @Mock
    private ExtractorAdapter adapter;

    private ExtractorService extractorService;

    @BeforeEach
    void setUp() {
        when(factory.getAdapter()).thenReturn(adapter);
        extractorService = new ExtractorService(factory);
        extractorService.init();
    }

    @Test
    void executeWorkflow_shouldDownloadUploadAndShare() {
        String sourceUrl = "https://public.example.com/calendar.ics?signature=abc";
        String destinationRemote = "my-bucket/raw/job-2026-03-12-001/calendar.ics";
        int expirationTime = 3600;
        byte[] content = "BEGIN:VCALENDAR".getBytes();
        String sharedUrl = "https://bucket.example.com/calendar.ics?signature=xyz";

        when(adapter.download(sourceUrl)).thenReturn(content);
        when(adapter.share(destinationRemote, expirationTime)).thenReturn(sharedUrl);

        String result = extractorService.executeWorkflow(sourceUrl, destinationRemote, expirationTime);

        InOrder inOrder = inOrder(adapter);
        inOrder.verify(adapter).download(sourceUrl);
        inOrder.verify(adapter).upload(destinationRemote, content);
        inOrder.verify(adapter).share(destinationRemote, expirationTime);

        assertEquals(sharedUrl, result);
    }
}
