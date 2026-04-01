package com.example.extractorservice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import com.example.extractorservice.config.DotenvInitializer;

@SpringBootApplication
public class ExtractorServiceApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ExtractorServiceApplication.class)
                .initializers(new DotenvInitializer())
                .run(args);
    }

}
