package com.example.extractorservice.adapter;

import java.util.List;

public interface ExtractorAdapter {
    void upload(String remoteSrc, byte[] content);

    byte[] download(String remoteSrc);

    void update(String remoteSrc, byte[] content);

    void delete(String remoteSrc, boolean recursive);

    List<String> list(String remoteSrc);

    boolean doesExists(String remoteSrc);

    String share(String remoteSrc, int expirationTime);
}
