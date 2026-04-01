package com.example.extractorservice.factory;

import java.util.Map;
import org.springframework.stereotype.Component;

import com.example.extractorservice.adapter.ExtractorAdapter;

@Component
public class ExtractorServiceFactory {

    private final Map<String, ExtractorAdapter> adapters;

    public ExtractorServiceFactory(Map<String, ExtractorAdapter> adapters) {
        this.adapters = adapters;
    }

    public ExtractorAdapter getAdapter() {
        String provider = System.getProperty("PROVIDER_IMPL");

        ExtractorAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
        return adapter;
    }
}
