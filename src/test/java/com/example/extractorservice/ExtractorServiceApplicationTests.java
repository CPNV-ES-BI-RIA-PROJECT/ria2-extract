package com.example.extractorservice;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.extractorservice.adapter.ExtractorAdapter;
import com.example.extractorservice.factory.ExtractorServiceFactory;

@SpringBootTest
@ActiveProfiles("test")
class ExtractorServiceApplicationTests {

    @MockitoBean
    private ExtractorServiceFactory factory;

    @BeforeEach
    void setup() {
        when(factory.getAdapter()).thenReturn(mock(ExtractorAdapter.class));
    }

    @Test
    void contextLoads() {
    }

}
