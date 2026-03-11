/**
 * Implementation of GCP Storage Bucket Adapter.
 */
package com.example.extractorservice.adapter.impl;

import com.example.extractorservice.adapter.ExtractorAdapter;
import com.example.extractorservice.exception.BucketObjectNotFoundException;
import com.example.extractorservice.exception.BucketOperationException;
import com.example.extractorservice.helper.AdapterHelper;
import com.example.extractorservice.helper.RemoteDownloadHelper;

import static com.example.extractorservice.helper.ConfigHelper.getConfig;
import static com.example.extractorservice.helper.AdapterHelper.validateExpiration;
import static com.example.extractorservice.helper.AdapterHelper.validateNotRoot;
import static com.example.extractorservice.helper.AdapterHelper.validateRemoteSrc;
import static com.example.extractorservice.helper.AdapterHelper.BucketSrc;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.StreamSupport;

@Component("GCP")
@Profile("!test")
public class GcpAdapterImpl implements ExtractorAdapter {

    private final Storage storage;

    public GcpAdapterImpl() {
        this(createStorageClient());
    }

    /**
     * Constructor with parameters for testing.
     * 
     * @param storage GCP Storage client
     */
    GcpAdapterImpl(final Storage storage) {
        this.storage = storage;
    }

    @Override
    public void upload(final String remoteSrc, final byte[] content) {
        try {
            BucketSrc bucketSrc = AdapterHelper.extractBucketAndKey(remoteSrc);

            BlobId blobId = BlobId.of(bucketSrc.bucket(), bucketSrc.key());
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

            storage.create(blobInfo, content);

        } catch (Exception e) {
            throw new BucketOperationException(
                    "GCP error while uploading object to " + remoteSrc, e);
        }
    }

    @Override
    public byte[] download(final String remoteSrc) {
        if (RemoteDownloadHelper.isHttpUrl(remoteSrc)) {
            return RemoteDownloadHelper.download(remoteSrc);
        }

        BucketSrc bucketSrc = AdapterHelper.extractBucketAndKey(remoteSrc);

        try {
            Blob blob = storage.get(bucketSrc.bucket(), bucketSrc.key());

            if (blob == null) {
                throw new BucketObjectNotFoundException(remoteSrc);
            }

            return blob.getContent();
        } catch (BucketObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BucketOperationException(
                    "GCP error while downloading object from " + remoteSrc, e);
        }
    }

    @Override
    public void update(final String remoteSrc, final byte[] content) {
        // In GCP, upload overwrites by default :
        // https://docs.cloud.google.com/storage/docs/json_api/v1/objects/insert
        upload(remoteSrc, content);
    }

    @Override
    public void delete(final String remoteSrc, final boolean recursive) {
        validateRemoteSrc(remoteSrc);
        validateNotRoot(remoteSrc);

        String normalizedRemoteSrc = "";
        BucketSrc bucketSrc = AdapterHelper.extractBucketAndKey(remoteSrc);

        try {

            if (!recursive) {
                boolean deleted = storage.delete(bucketSrc.bucket(), bucketSrc.key());
                if (!deleted) {
                    throw new BucketObjectNotFoundException(bucketSrc.key());
                }
                return;
            }

            normalizedRemoteSrc = bucketSrc.key();

            Page<Blob> blobs = storage.list(
                    bucketSrc.bucket(),
                    Storage.BlobListOption.prefix(
                            normalizedRemoteSrc.endsWith("/") ? normalizedRemoteSrc : normalizedRemoteSrc + "/"));

            List<BlobId> toDelete = StreamSupport.stream(blobs.iterateAll().spliterator(), false)
                    .map(Blob::getBlobId)
                    .toList();

            if (toDelete.isEmpty()) {
                throw new BucketObjectNotFoundException(normalizedRemoteSrc);
            }

            storage.delete(toDelete);

        } catch (BucketObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BucketOperationException(
                    "GCP error while deleting file(s) at " + remoteSrc, e);
        }
    }

    @Override
    public List<String> list(final String remoteSrc) {
        BucketSrc bucketSrc = AdapterHelper.extractBucketAndKey(remoteSrc);

        try {
            Page<Blob> blobs = storage.list(
                    bucketSrc.bucket(),
                    Storage.BlobListOption.prefix(bucketSrc.key()));

            return StreamSupport.stream(blobs.iterateAll().spliterator(), false)
                    .map(Blob::getName)
                    .toList();

        } catch (Exception e) {
            throw new BucketOperationException(
                    "GCP error while listing files with prefix " + remoteSrc, e);
        }
    }

    @Override
    public boolean doesExists(final String remoteSrc) {
        validateRemoteSrc(remoteSrc);
        BucketSrc bucketSrc = AdapterHelper.extractBucketAndKey(remoteSrc);

        try {
            return storage.get(bucketSrc.bucket(), bucketSrc.key()) != null;
        } catch (Exception e) {
            throw new BucketOperationException(
                    "GCP error while checking existence of " + remoteSrc,
                    e);
        }
    }

    @Override
    public String share(final String remoteSrc, final int expirationTime) {
        validateRemoteSrc(remoteSrc);
        validateExpiration(expirationTime);

        BucketSrc bucketSrc = AdapterHelper.extractBucketAndKey(remoteSrc);

        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(bucketSrc.bucket(), bucketSrc.key()).build();

            return storage.signUrl(
                    blobInfo,
                    expirationTime,
                    java.util.concurrent.TimeUnit.SECONDS,
                    Storage.SignUrlOption.withV4Signature()).toString();

        } catch (Exception e) {
            throw new BucketOperationException(
                    "GCP error while generating signed URL for " + remoteSrc, e);
        }
    }

    // ---------------- PRIVATE ---------------- //

    /**
     * Create GCP Storage client.
     * 
     * @return Storage client
     */
    private static Storage createStorageClient() {
        String credentialsPath = getConfig(
                "GOOGLE_APPLICATION_CREDENTIALS",
                "GCP credentials path");

        try (InputStream in = Files.newInputStream(Paths.get(credentialsPath))) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in);
            return StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .build()
                    .getService();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load GCP credentials", e);
        }
    }
}
