package com.example.extractorservice.exception;

public class InvalidBucketPathException extends RuntimeException {
    public InvalidBucketPathException(String message) {
        super(message);
    }
}
