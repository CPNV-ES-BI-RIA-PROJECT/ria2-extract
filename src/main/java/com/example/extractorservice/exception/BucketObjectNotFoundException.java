package com.example.extractorservice.exception;

public class BucketObjectNotFoundException extends RuntimeException {
    public BucketObjectNotFoundException(String key) {
        super("Object not found in bucket: " + key);
    }
}
