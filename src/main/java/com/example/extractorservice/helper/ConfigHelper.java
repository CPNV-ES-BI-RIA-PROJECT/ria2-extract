package com.example.extractorservice.helper;

public class ConfigHelper {

    /**
     * Utility method to get configuration from system property or environment
     * variable.
     * 
     * @param envVar     - environment variable name
     * @param configName - configuration descriptive name
     * @return configuration value
     */
    public static String getConfig(String envVar, String configName) {
        // 1. FIRST: Check Docker/container environment variables (highest priority)
        String value = System.getenv(envVar);

        // 2. SECOND: Check system properties (set by DotenvInitializer from .env)
        if (value == null || value.isBlank()) {
            value = System.getProperty(envVar); // AWS_BUCKET_NAME
        }

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    configName + " is not configured.\n" +
                            "When running locally: Add to .env file as " + envVar + "=value\n" +
                            "When running in Docker: Set environment variable " + envVar);
        }
        return value;
    }
}
