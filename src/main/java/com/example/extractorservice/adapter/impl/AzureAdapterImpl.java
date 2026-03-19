package com.example.extractorservice.adapter.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.extractorservice.adapter.ExtractorAdapter;

import java.util.List;

@Component("AZURE")
@ConditionalOnProperty(name = "PROVIDER_IMPL", havingValue = "AZURE")
public class AzureAdapterImpl implements ExtractorAdapter {

    @Override
    public byte[] download(String remoteSrc) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'download'");
    }

    @Override
    public void update(String remoteSrc, byte[] content) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'update'");
    }

    @Override
    public void delete(String remoteSrc, boolean recursive) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'delete'");
    }

    @Override
    public List<String> list(String remoteSrc) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'list'");
    }

    @Override
    public boolean doesExists(String remoteSrc) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'doesExists'");
    }

    @Override
    public String share(String remoteSrc, int expirationTime) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'share'");
    }

    @Override
    public void upload(String remoteSrc, byte[] content) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'upload'");
    }
}
