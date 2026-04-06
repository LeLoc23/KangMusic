package com.musicapp.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Local-filesystem implementation of {@link StorageService}.
 *
 * Files are written to the directory configured by {@code app.upload.dir}
 * (different per Spring profile). The serving URL uses the {@code /stream/**}
 * byte-range controller so the HTML5 audio seek bar works correctly.
 *
 * To switch to S3 later:
 *   1. Create {@code S3StorageService implements StorageService}
 *   2. Annotate it with {@code @Profile("s3")} (and this class with {@code @Profile("!s3")})
 *   3. Set {@code spring.profiles.active=s3} in production config
 */
@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public String store(MultipartFile file, String extension) throws IOException {
        String safeFileName = UUID.randomUUID() + "." + extension;

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        Path targetPath = uploadPath.resolve(safeFileName).normalize();
        if (!targetPath.startsWith(uploadPath)) {
            throw new SecurityException("Path traversal attempt detected.");
        }

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Stored file '{}' → '{}'", safeFileName, targetPath);
        return safeFileName;
    }

    @Override
    public void delete(String fileKey) throws IOException {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(fileKey).normalize();

        if (!filePath.startsWith(uploadPath)) {
            throw new SecurityException("Path traversal attempt on delete: " + fileKey);
        }

        boolean deleted = Files.deleteIfExists(filePath);
        if (deleted) {
            log.debug("Deleted file '{}'", filePath);
        } else {
            log.warn("File '{}' did not exist on disk during delete (already gone?)", filePath);
        }
    }

    @Override
    public String getServeUrl(String fileKey) {
        return "/stream/" + fileKey;
    }

    /**
     * Returns the resolved absolute path for a given file key.
     * Used by {@link OrphanFileCleanupJob} to check whether a file still exists.
     */
    public Path resolveAbsolutePath(String fileKey) {
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve(fileKey).normalize();
    }
}
