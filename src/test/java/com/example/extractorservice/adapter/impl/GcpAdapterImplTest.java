package com.example.extractorservice.adapter.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.extractorservice.exception.BucketObjectNotFoundException;
import com.example.extractorservice.exception.BucketOperationException;
import com.example.extractorservice.exception.InvalidBucketPathException;
import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;

@ExtendWith(MockitoExtension.class)
public class GcpAdapterImplTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private Storage storage;

    @Mock
    private Page<Blob> page;

    @Mock
    private Blob blob1;

    @Mock
    private Blob blob2;

    private GcpAdapterImpl adapter;

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
        adapter = new GcpAdapterImpl(storage);
    }

    // ------------------------------------------------------------------
    // Upload method tests
    // ------------------------------------------------------------------

    @Test
    void upload_shouldCallStorageCreate_whenObjectInputIsValid() {
        String remotePath = BUCKET + "/dir/file.txt";
        byte[] content = "dummy content".getBytes();

        adapter.upload(remotePath, content);

        verify(storage).create(any(BlobInfo.class), eq(content));
    }

    @Test
    void upload_shouldThrowBucketOperationException_whenGcpFails() {
        byte[] content = "dummy".getBytes();
        when(storage.create(any(BlobInfo.class), any(byte[].class)))
                .thenThrow(new RuntimeException("GCP failure"));

        BucketOperationException ex = assertThrows(
                BucketOperationException.class,
                () -> adapter.upload(BUCKET + "/dir/file.txt", content));

        assertTrue(ex.getMessage().contains("GCP error"));
    }

    // ------------------------------------------------------------------
    // Download method tests
    // ------------------------------------------------------------------

    @Test
    void download_shouldReturnObjectBytes_whenObjectExists() {
        String remoteSrc = BUCKET + "/dir/file.txt";
        byte[] content = "file-content".getBytes();

        when(storage.get(BUCKET, "dir/file.txt")).thenReturn(blob1);
        when(blob1.getContent()).thenReturn(content);

        byte[] result = adapter.download(remoteSrc);

        assertArrayEquals(content, result);
        verify(storage).get(BUCKET, "dir/file.txt");
        verify(blob1).getContent();
    }

    @Test
    void download_shouldThrowBucketObjectNotFoundException_whenObjectDoesNotExist() {
        String remoteSrc = BUCKET + "/missing/file.txt";
        when(storage.get(BUCKET, "missing/file.txt")).thenReturn(null);

        BucketObjectNotFoundException ex = assertThrows(
                BucketObjectNotFoundException.class,
                () -> adapter.download(remoteSrc));

        assertTrue(ex.getMessage().contains(remoteSrc));
        verify(storage).get(BUCKET, "missing/file.txt");
    }

    @Test
    void download_shouldThrowBucketOperationException_whenGcpFails() {
        String remoteSrc = BUCKET + "/dir/file.txt";
        when(storage.get(BUCKET, "dir/file.txt")).thenReturn(blob1);
        when(blob1.getContent()).thenThrow(new RuntimeException("GCP download failure"));

        BucketOperationException ex = assertThrows(
                BucketOperationException.class,
                () -> adapter.download(remoteSrc));

        assertTrue(ex.getMessage().contains("GCP error while downloading"));
        verify(storage).get(BUCKET, "dir/file.txt");
        verify(blob1).getContent();
    }

    // ------------------------------------------------------------------
    // Update method tests
    // ------------------------------------------------------------------

    @Test
    void update_shouldDelegateToUpload_whenCalled() {
        byte[] content = "updated".getBytes();

        assertDoesNotThrow(() -> adapter.update(BUCKET + "/dir/file.txt", content));
        verify(storage).create(any(BlobInfo.class), eq(content));
    }

    @Test
    void update_shouldThrowBucketOperationException_whenGcpFails() {
        byte[] content = "updated".getBytes();
        when(storage.create(any(BlobInfo.class), any(byte[].class)))
                .thenThrow(new RuntimeException("GCP failure"));

        BucketOperationException ex = assertThrows(
                BucketOperationException.class,
                () -> adapter.update(BUCKET + "/dir/file.txt", content));

        assertTrue(ex.getMessage().contains("GCP error while uploading object"));
    }

    // ------------------------------------------------------------------
    // Delete method tests
    // ------------------------------------------------------------------

    @Test
    void delete_shouldDeleteSingleObject_whenRecursiveIsFalse() {
        when(storage.delete(BUCKET, "file.txt")).thenReturn(true);

        adapter.delete(BUCKET + "/file.txt", false);

        verify(storage).delete(BUCKET, "file.txt");
    }

    @Test
    void delete_shouldDeleteAllObjects_whenRecursiveIsTrue() {
        Blob recursiveBlob1 = mock(Blob.class);
        Blob recursiveBlob2 = mock(Blob.class);

        when(recursiveBlob1.getBlobId()).thenReturn(BlobId.of(BUCKET, "dir/file1.txt"));
        when(recursiveBlob2.getBlobId()).thenReturn(BlobId.of(BUCKET, "dir/sub/file2.txt"));

        @SuppressWarnings("unchecked")
        Page<Blob> recursivePage = mock(Page.class);
        when(recursivePage.iterateAll()).thenReturn(List.of(recursiveBlob1, recursiveBlob2));

        when(storage.list(anyString(), any(Storage.BlobListOption.class))).thenReturn(recursivePage);

        adapter.delete("dir", true);

        verify(storage).delete(eq(List.of(
                BlobId.of(BUCKET, "dir/file1.txt"),
                BlobId.of(BUCKET, "dir/sub/file2.txt"))));
    }

    @Test
    void delete_shouldThrowInvalidBucketPathException_whenRemoteSrcIsNull() {
        assertThrows(InvalidBucketPathException.class, () -> adapter.delete(null, false));
        verifyNoInteractions(storage);
    }

    @Test
    void delete_shouldThrowInvalidBucketPathException_whenDeletingRoot() {
        assertThrows(InvalidBucketPathException.class, () -> adapter.delete("/", false));
        verifyNoInteractions(storage);
    }

    @Test
    void delete_shouldThrowBucketObjectNotFoundException_whenObjectDoesNotExist_simple() {
        when(storage.delete(BUCKET, "missing-file.txt")).thenReturn(false);

        assertThrows(
                BucketObjectNotFoundException.class,
                () -> adapter.delete(BUCKET + "/missing-file.txt", false));

        verify(storage).delete(BUCKET, "missing-file.txt");
    }

    @Test
    void delete_shouldThrowBucketObjectNotFoundException_whenNoObjectsFound_recursive() {
        @SuppressWarnings("unchecked")
        Page<Blob> emptyPage = mock(Page.class);
        when(emptyPage.iterateAll()).thenReturn(List.of());
        when(storage.list(anyString(), any(Storage.BlobListOption.class))).thenReturn(emptyPage);

        assertThrows(BucketObjectNotFoundException.class, () -> adapter.delete("empty", true));
    }

    @Test
    void delete_shouldThrowBucketOperationException_whenGcpDeleteFails() {
        when(storage.delete(BUCKET, "dir/file.txt")).thenThrow(new RuntimeException("GCP failure"));

        assertThrows(BucketOperationException.class,
                () -> adapter.delete("dir/file.txt", false));
    }

    @Test
    void delete_shouldThrowBucketOperationException_whenGcpListFails() {
        when(storage.list(eq(BUCKET), any(Storage.BlobListOption.class)))
                .thenThrow(new RuntimeException("GCP failure"));

        assertThrows(BucketOperationException.class,
                () -> adapter.delete("dir", true));
    }

    // ------------------------------------------------------------------
    // List method tests
    // ------------------------------------------------------------------

    @Test
    void list_shouldReturnObjectNames_whenObjectsExist() {
        when(blob1.getName()).thenReturn("file1.txt");
        when(blob2.getName()).thenReturn("dir/file2.txt");
        when(page.iterateAll()).thenReturn(List.of(blob1, blob2));
        when(storage.list(eq(BUCKET), any(Storage.BlobListOption.class))).thenReturn(page);

        List<String> result = adapter.list(BUCKET + "/dir/");

        assertNotNull(result);
        assertEquals(List.of("file1.txt", "dir/file2.txt"), result);
        verify(storage).list(eq(BUCKET), any(Storage.BlobListOption.class));
    }

    @Test
    void list_shouldReturnEmptyList_whenNoObjectsFound() {
        when(page.iterateAll()).thenReturn(List.of());
        when(storage.list(eq(BUCKET), any(Storage.BlobListOption.class))).thenReturn(page);

        List<String> result = adapter.list(BUCKET + "/empty/");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void list_shouldThrowBucketOperationException_whenGcpFails() {
        when(storage.list(eq(BUCKET), any(Storage.BlobListOption.class)))
                .thenThrow(new RuntimeException("GCP failure"));

        BucketOperationException ex = assertThrows(
                BucketOperationException.class,
                () -> adapter.list(BUCKET + "/dir/"));

        assertTrue(ex.getMessage().contains("GCP error"));
    }

    // ------------------------------------------------------------------
    // doesExists method tests
    // ------------------------------------------------------------------

    @Test
    void doesExists_shouldReturnTrue_whenObjectExists() {
        when(storage.get(BUCKET, "dir/file.txt")).thenReturn(blob1);

        boolean exists = adapter.doesExists(BUCKET + "/dir/file.txt");

        assertTrue(exists);
        verify(storage).get(BUCKET, "dir/file.txt");
    }

    @Test
    void doesExists_shouldReturnFalse_whenObjectDoesNotExist() {
        when(storage.get(BUCKET, "dir/missing.txt")).thenReturn(null);

        boolean exists = adapter.doesExists(BUCKET + "/dir/missing.txt");

        assertFalse(exists);
        verify(storage).get(BUCKET, "dir/missing.txt");
    }

    @Test
    void doesExists_shouldThrowInvalidBucketPathException_whenRemoteSrcIsNull() {
        assertThrows(InvalidBucketPathException.class,
                () -> adapter.doesExists(null));
        verifyNoInteractions(storage);
    }

    @Test
    void doesExists_shouldThrowBucketOperationException_whenGcpFails() {
        when(storage.get(BUCKET, "dir/file.txt")).thenThrow(new RuntimeException("GCP failure"));

        BucketOperationException ex = assertThrows(BucketOperationException.class,
                () -> adapter.doesExists(BUCKET + "/dir/file.txt"));

        assertTrue(ex.getMessage().contains("GCP error"));
    }

    // ------------------------------------------------------------------
    // share method tests
    // ------------------------------------------------------------------

    @Test
    void share_shouldReturnSignedUrl_whenInputsAreValid() throws Exception {
        int expiration = 3600;
        URL signedUrl = URI.create(
                "https://storage.googleapis.com/test-bucket/dir/file.txt?X-Goog-Signature=abc").toURL();

        when(storage.signUrl(
                any(BlobInfo.class),
                eq((long) expiration),
                eq(TimeUnit.SECONDS),
                any(Storage.SignUrlOption.class))).thenReturn(signedUrl);

        String result = adapter.share("dir/file.txt", expiration);

        assertNotNull(result);
        assertTrue(result.contains("X-Goog-Signature"));
    }

    @Test
    void share_shouldThrowInvalidBucketPathException_whenRemoteSrcIsNull() {
        assertThrows(InvalidBucketPathException.class, () -> adapter.share(null, 3600));
        verifyNoInteractions(storage);
    }

    @Test
    void share_shouldThrowInvalidBucketPathException_whenExpirationIsInvalid() {
        assertThrows(InvalidBucketPathException.class, () -> adapter.share("dir/file.txt", 0));
        verifyNoInteractions(storage);
    }

    @Test
    void share_shouldThrowBucketOperationException_whenGcpFails() {
        when(storage.signUrl(
                any(BlobInfo.class),
                anyLong(),
                any(TimeUnit.class),
                any(Storage.SignUrlOption.class))).thenThrow(new RuntimeException("GCP failure"));

        BucketOperationException ex = assertThrows(BucketOperationException.class,
                () -> adapter.share("dir/file.txt", 3600));

        assertTrue(ex.getMessage().contains("GCP error"));
    }
}
