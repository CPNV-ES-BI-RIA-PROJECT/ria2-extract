package com.example.extractorservice.helper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.extractorservice.exception.BucketObjectNotFoundException;
import com.example.extractorservice.exception.BucketOperationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class RemoteDownloadHelperTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void isHttpUrl_shouldReturnTrue_forHttpUrl() {
        assertTrue(RemoteDownloadHelper.isHttpUrl(baseUrl + "/file"));
    }

    @Test
    void isHttpUrl_shouldReturnFalse_forBucketPath() {
        assertFalse(RemoteDownloadHelper.isHttpUrl("my-bucket/path/file.csv"));
    }

    @Test
    void download_shouldReturnResponseBody_whenEndpointReturnsSuccess() {
        byte[] expected = "order-id,amount\n1,42\n".getBytes(StandardCharsets.UTF_8);
        server.createContext("/file", new FixedResponseHandler(200, expected));

        byte[] result = RemoteDownloadHelper.download(baseUrl + "/file");

        assertArrayEquals(expected, result);
    }

    @Test
    void download_shouldThrowBucketObjectNotFoundException_whenEndpointReturns404() {
        server.createContext("/missing", new FixedResponseHandler(404, new byte[0]));

        assertThrows(
                BucketObjectNotFoundException.class,
                () -> RemoteDownloadHelper.download(baseUrl + "/missing"));
    }

    @Test
    void download_shouldThrowBucketOperationException_whenEndpointReturnsNonSuccess() {
        server.createContext("/error", new FixedResponseHandler(500, "boom".getBytes(StandardCharsets.UTF_8)));

        assertThrows(
                BucketOperationException.class,
                () -> RemoteDownloadHelper.download(baseUrl + "/error"));
    }

    @Test
    void download_shouldFollowRedirects() {
        byte[] expected = "image-bytes".getBytes(StandardCharsets.UTF_8);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl + "/final-file");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final-file", new FixedResponseHandler(200, expected));

        byte[] result = RemoteDownloadHelper.download(baseUrl + "/redirect");

        assertArrayEquals(expected, result);
    }

    private static final class FixedResponseHandler implements HttpHandler {

        private final int statusCode;
        private final byte[] body;

        private FixedResponseHandler(int statusCode, byte[] body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(statusCode, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }
}
