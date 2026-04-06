package com.musicapp.services;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Storage abstraction that decouples MediaService from any particular
 * storage backend (local filesystem, S3, GCS, etc.).
 *
 * To add S3 support later, simply implement this interface in an
 * {@code S3StorageService} bean and activate it via a Spring profile.
 */
public interface StorageService {

    /**
     * Stores an uploaded file and returns an opaque file key that can be
     * later used to build a serving URL or delete the file.
     *
     * @param file      the uploaded multipart file
     * @param extension the file extension (without dot), e.g. "mp3", "mp4"
     * @return an opaque file key (UUID-based filename on local disk, S3 object key on cloud)
     * @throws IOException if the write fails
     */
    String store(MultipartFile file, String extension) throws IOException;

    /**
     * Deletes the file identified by the given key.
     * Implementations must be idempotent — deleting a non-existent key is a no-op.
     *
     * @param fileKey the key returned by {@link #store}
     * @throws IOException if deletion fails unexpectedly
     */
    void delete(String fileKey) throws IOException;

    /**
     * Returns the URL path that the client browser uses to stream the file.
     * For local storage this returns {@code /stream/<fileKey>}.
     * For S3 this would return a pre-signed URL or a CDN URL.
     *
     * @param fileKey the key returned by {@link #store}
     * @return a URL string suitable for use in {@code <audio src="...">}
     */
    String getServeUrl(String fileKey);
}
