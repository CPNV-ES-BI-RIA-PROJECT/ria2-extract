package com.example.extractorservice.adapter.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.extractorservice.exception.BucketObjectNotFoundException;
import com.example.extractorservice.exception.BucketOperationException;
import com.example.extractorservice.exception.InvalidBucketPathException;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

public class AwsAdapterImplTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private AwsAdapterImpl adapter;
    private AutoCloseable closeable;

    private final Supplier<S3Presigner> presignerSupplier = () -> s3Presigner;

    @BeforeAll
    static void beforeAll() {
        System.setProperty("SHARE_LINK_MAX_EXPIRATION_TIME", "604800");
    }

    @AfterAll
    static void tearDownAfterAll() {
        System.clearProperty("SHARE_LINK_MAX_EXPIRATION_TIME");
    }

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        adapter = new AwsAdapterImpl(s3Client, presignerSupplier);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    // ------------------------------------------------------------------
    // Upload method tests
    // ------------------------------------------------------------------

    @Test
    void upload_shouldUploadObject_whenInputIsValid() throws Exception {
        String remoteSrc = "test-bucket/dir/file.txt";
        byte[] content = "test-content".getBytes();

        assertDoesNotThrow(() -> adapter.upload(remoteSrc, content));

        verify(s3Client, times(1)).putObject(
                argThat((PutObjectRequest req) -> req.bucket().equals("test-bucket")
                        && req.key().equals("dir/file.txt")),
                any(RequestBody.class));
    }

    @Test
    void upload_shouldFail_whenRemoteSrcIsInvalid() throws Exception {
        assertThrows(InvalidBucketPathException.class,
                () -> adapter.upload("   ", "x".getBytes()));
        verifyNoInteractions(s3Client);
    }

    @Test
    void upload_shouldThrowBucketOperationException_whenS3Fails() throws Exception {
        doThrow(S3Exception.builder().statusCode(500).message("AWS failure").build())
                .when(s3Client)
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThrows(BucketOperationException.class,
                () -> adapter.upload("test-bucket/file.txt", "x".getBytes()));
    }

    // ------------------------------------------------------------------
    // Download method tests
    // ------------------------------------------------------------------

    @Test
    void download_shouldReturnObjectBytes_whenObjectExists() throws Exception {
        String remoteSrc = "test-bucket/dir/file.txt";
        byte[] expected = "payload".getBytes();

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), expected));

        byte[] result = adapter.download(remoteSrc);

        assertArrayEquals(expected, result);
        verify(s3Client, times(1)).headObject(any(HeadObjectRequest.class));
        verify(s3Client, times(1)).getObjectAsBytes(
                argThat((GetObjectRequest req) -> req.bucket().equals("test-bucket")
                        && req.key().equals("dir/file.txt")));
    }

    @Test
    void download_shouldFail_whenObjectDoesNotExist() throws Exception {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        assertThrows(BucketObjectNotFoundException.class,
                () -> adapter.download("test-bucket/missing/file.txt"));

        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void download_shouldThrowBucketOperationException_whenS3Fails() throws Exception {
        String remoteSrc = "test-bucket/dir/file.txt";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).build());

        assertThrows(BucketOperationException.class, () -> adapter.download(remoteSrc));
    }

    // ------------------------------------------------------------------
    // Update method tests
    // ------------------------------------------------------------------

    @Test
    void update_shouldReplaceObject_whenObjectExists() throws Exception {
        String remoteSrc = "test-bucket/dir/file.txt";
        byte[] content = "new-content".getBytes();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertDoesNotThrow(() -> adapter.update(remoteSrc, content));

        verify(s3Client, times(1)).headObject(any(HeadObjectRequest.class));
        verify(s3Client, times(1)).putObject(
                argThat((PutObjectRequest req) -> req.bucket().equals("test-bucket")
                        && req.key().equals("dir/file.txt")),
                any(RequestBody.class));
    }

    @Test
    void update_shouldFail_whenRemoteObjectDoesNotExist() throws Exception {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        assertThrows(BucketObjectNotFoundException.class,
                () -> adapter.update("test-bucket/missing/file.txt", "x".getBytes()));

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void update_shouldThrowBucketOperationException_whenS3Fails() throws Exception {
        String remoteSrc = "test-bucket/dir/file.txt";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());
        doThrow(S3Exception.builder().statusCode(500).build())
                .when(s3Client)
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThrows(BucketOperationException.class,
                () -> adapter.update(remoteSrc, "x".getBytes()));
    }

    // ------------------------------------------------------------------
    // Delete method tests
    // ------------------------------------------------------------------

    @Test
    void delete_shouldDeleteObject_whenRecursiveFalse() throws Exception {
        String remoteSrc = "test-bucket/dir/file.txt";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertDoesNotThrow(() -> adapter.delete(remoteSrc, false));

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_shouldFail_whenObjectDoesNotExist_andNotRecursive() throws Exception {
        String remoteSrc = "test-bucket/missing/file.txt";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        assertThrows(BucketObjectNotFoundException.class,
                () -> adapter.delete(remoteSrc, false));

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_shouldDeleteObjectsRecursively() throws Exception {
        String remoteSrc = "test-bucket/dir";
        List<S3Object> objects = List.of(
                S3Object.builder().key("dir/file1.txt").build(),
                S3Object.builder().key("dir/sub/file2.txt").build());

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(objects).build());

        assertDoesNotThrow(() -> adapter.delete(remoteSrc, true));

        verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void delete_shouldFail_whenNoObjectsFoundRecursively() throws Exception {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(List.of()).build());

        assertThrows(BucketObjectNotFoundException.class,
                () -> adapter.delete("test-bucket/empty", true));

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void delete_shouldFail_whenRemoteSrcIsInvalid() throws Exception {
        assertThrows(InvalidBucketPathException.class,
                () -> adapter.delete("", false));
        verifyNoInteractions(s3Client);
    }

    @Test
    void delete_shouldFail_whenRemoteSrcIsRoot() throws Exception {
        assertThrows(InvalidBucketPathException.class,
                () -> adapter.delete("/", true));
        verifyNoInteractions(s3Client);
    }

    @Test
    void delete_shouldThrowBucketOperationException_whenAwsThrowsException() throws Exception {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());
        doThrow(S3Exception.builder().statusCode(500).build())
                .when(s3Client)
                .deleteObject(any(DeleteObjectRequest.class));

        assertThrows(BucketOperationException.class,
                () -> adapter.delete("test-bucket/dir/file.txt", false));
    }

    // ------------------------------------------------------------------
    // List method tests
    // ------------------------------------------------------------------

    @Test
    void list_shouldReturnObjects_whenObjectsExist() throws Exception {
        String prefix = "test-bucket/dir/";
        S3Object obj1 = S3Object.builder().key("file1.txt").build();
        S3Object obj2 = S3Object.builder().key("dir/file2.txt").build();

        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(Arrays.asList(obj1, obj2))
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        List<String> result = adapter.list(prefix);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("file1.txt"));
        assertTrue(result.contains("dir/file2.txt"));
    }

    @Test
    void list_shouldReturnEmptyList_whenNoObjectsFound() throws Exception {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(List.of()).build());

        List<String> result = adapter.list("test-bucket/empty/");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void list_shouldThrowBucketOperationException_whenAwsFails() throws Exception {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(S3Exception.builder().statusCode(500).build());

        assertThrows(BucketOperationException.class,
                () -> adapter.list("test-bucket/dir/"));
    }

    // ------------------------------------------------------------------
    // doesExists method tests
    // ------------------------------------------------------------------

    @Test
    void doesExists_shouldReturnTrue_whenObjectExists() throws Exception {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertTrue(adapter.doesExists("test-bucket/dir/file.txt"));
    }

    @Test
    void doesExists_shouldReturnFalse_whenObjectDoesNotExist() throws Exception {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        assertFalse(adapter.doesExists("test-bucket/dir/missing.txt"));
    }

    @Test
    void doesExists_shouldFail_whenRemoteSrcIsNull() throws Exception {
        assertThrows(InvalidBucketPathException.class,
                () -> adapter.doesExists(null));
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void doesExists_shouldFail_whenRemoteSrcIsBlank() throws Exception {
        assertThrows(InvalidBucketPathException.class,
                () -> adapter.doesExists("   "));
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void doesExists_shouldThrowBucketOperationException_whenAwsFails() throws Exception {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).build());

        assertThrows(BucketOperationException.class,
                () -> adapter.doesExists("test-bucket/dir/file.txt"));
    }

    // ------------------------------------------------------------------
    // Share method tests
    // ------------------------------------------------------------------

    @Test
    void share_shouldReturnPresignedUrl_whenInputsAreValid() throws Exception {
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://signed-url").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

        String result = adapter.share("test-bucket/dir/file.txt", 3600);

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.contains("https://"));
        verify(s3Presigner, times(1))
                .presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void share_shouldFail_whenRemoteSrcIsNull() throws Exception {
        assertThrows(InvalidBucketPathException.class,
                () -> adapter.share(null, 3600));
        verifyNoInteractions(s3Presigner);
    }

    @Test
    void share_shouldFail_whenRemoteSrcIsBlank() throws Exception {
        assertThrows(InvalidBucketPathException.class,
                () -> adapter.share("   ", 3600));
        verifyNoInteractions(s3Presigner);
    }

    @Test
    void share_shouldFail_whenExpirationIsZero() throws Exception {
        assertThrows(InvalidBucketPathException.class,
                () -> adapter.share("test-bucket/dir/file.txt", 0));
        verifyNoInteractions(s3Presigner);
    }

    @Test
    void share_shouldFail_whenExpirationIsTooLarge() throws Exception {
        int moreThan7Days = 7 * 24 * 3600 + 1;
        assertThrows(InvalidBucketPathException.class,
                () -> adapter.share("test-bucket/dir/file.txt", moreThan7Days));
        verifyNoInteractions(s3Presigner);
    }

    @Test
    void share_shouldFail_whenAwsThrowsException() throws Exception {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(S3Exception.builder().message("AWS error").build());

        assertThrows(BucketOperationException.class,
                () -> adapter.share("test-bucket/dir/file.txt", 3600));
    }
}
