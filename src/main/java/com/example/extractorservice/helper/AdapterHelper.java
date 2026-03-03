package com.example.extractorservice.helper;

import static com.example.extractorservice.helper.ConfigHelper.getConfig;

import com.example.extractorservice.exception.InvalidBucketPathException;

public class AdapterHelper {

    private AdapterHelper() {
        // private constructor to prevent instantiation
    }

    public record BucketSrc(String bucket, String key) {
    }

    /**
     * Extract bucket and key from remoteSrc
     * 
     * @param remoteSrc
     * @return BucketSrc
     */
    public static BucketSrc extractBucketAndKey(String remoteSrc) {
        validateRemoteSrc(remoteSrc);

        String normalized = remoteSrc.trim();

        // Remove leading slash
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        int firstSlash = normalized.indexOf('/');

        // Case: "bucket" or "bucket/"
        if (firstSlash == -1) {
            return new BucketSrc(normalized, "");
        }

        String bucket = normalized.substring(0, firstSlash);
        String key = normalized.substring(firstSlash + 1);

        return new BucketSrc(bucket, key);
    }

    /**
     * Validate that remoteSrc exists
     * 
     * @param remoteSrc
     */
    public static void validateRemoteSrc(String remoteSrc) {
        if (remoteSrc == null || remoteSrc.isBlank()) {
            throw new InvalidBucketPathException("remoteSrc must not be null or empty");
        }
    }

    /**
     * Valide that remoteSrc is not root folder
     * 
     * @param remoteSrc
     */
    public static void validateNotRoot(String remoteSrc) {
        if ("/".equals(remoteSrc)) {
            throw new InvalidBucketPathException("Root path '/' is forbidden");
        }
    }

    /**
     * Valide expiration time
     * 
     * @param expirationTime
     */
    public static void validateExpiration(int expirationTime) {
        String maxExpirationTime = getConfig("SHARE_LINK_MAX_EXPIRATION_TIME", "Share Link Max Expiration Time");
        int maxExpiration = Integer.parseInt(maxExpirationTime);

        if (expirationTime <= 0 || expirationTime > maxExpiration) {
            throw new InvalidBucketPathException(
                    "expirationTime must be between 1 second and " + maxExpiration + " seconds");
        }
    }

    /**
     * Normalize list prefix by ensuring it ends with a slash.
     * 
     * @param prefix
     * @return normalized prefix
     */
    public static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    /**
     * Validate that the key in BucketSrc is present
     * 
     * @param bucketSrc
     */
    public static void validateKeyRequired(BucketSrc bucketSrc) {
        if (bucketSrc.key() == null || bucketSrc.key().isBlank()) {
            throw new InvalidBucketPathException(
                    "Object key is required (bucket/key)");
        }
    }
}
