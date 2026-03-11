package com.example.extractorservice.helper;

import static com.example.extractorservice.helper.AdapterHelper.validateRemoteSrc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.example.extractorservice.exception.BucketObjectNotFoundException;
import com.example.extractorservice.exception.BucketOperationException;

public final class RemoteDownloadHelper {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private RemoteDownloadHelper() {
    }

    public static boolean isHttpUrl(String remoteSrc) {
        validateRemoteSrc(remoteSrc);

        try {
            URI uri = URI.create(remoteSrc.trim());
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static byte[] download(String remoteSrc) {
        validateRemoteSrc(remoteSrc);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(remoteSrc.trim()))
                    .GET()
                    .timeout(REQUEST_TIMEOUT)
                    .build();

            HttpResponse<byte[]> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }

            if (response.statusCode() == 404) {
                throw new BucketObjectNotFoundException(remoteSrc);
            }

            throw new BucketOperationException(
                    "Error downloading object from pre-signed URL "
                            + remoteSrc
                            + " (HTTP "
                            + response.statusCode()
                            + ")",
                    null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BucketOperationException(
                    "Interrupted while downloading object from pre-signed URL " + remoteSrc,
                    e);
        } catch (IOException | IllegalArgumentException e) {
            throw new BucketOperationException(
                    "Error downloading object from pre-signed URL " + remoteSrc,
                    e);
        }
    }
}
