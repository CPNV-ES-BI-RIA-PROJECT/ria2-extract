package com.example.extractorservice.service;

import org.springframework.stereotype.Service;

import com.example.extractorservice.adapter.ExtractorAdapter;
import com.example.extractorservice.factory.ExtractorServiceFactory;

import jakarta.annotation.PostConstruct;

import java.util.List;

@Service
public class ExtractorService {

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

    public boolean doesExists(String remote) {
        return adapter.doesExists(remote);
    }

    public String share(String remote, int expirationTime) {
        return adapter.share(remote, expirationTime);
    }

    public String executeWorkflow(String sourceUrl, String destinationRemote, int expirationTime) {
        byte[] content = adapter.download(sourceUrl);
        adapter.upload(destinationRemote, content);
        return adapter.share(destinationRemote, expirationTime);
    }
}
