package com.musicapp.services;

import com.musicapp.repositories.MediaItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Scheduled job that performs eventual-consistency cleanup of orphaned media files.
 *
 * <p>An orphaned file occurs when a media item is soft-deleted in the DB (deleted=true)
 * but the corresponding file on disk was not removed (e.g. due to an IO error during
 * the delete operation in {@link MediaService#deleteMedia}).
 *
 * <p>This job runs every hour and removes any such files, ensuring disk space is
 * eventually reclaimed even if the real-time delete failed.
 *
 * <p>Runs in the default Spring scheduling thread. It is intentionally NOT async
 * to avoid overlapping runs — the {@code fixedDelay} ensures each run only starts
 * after the previous one finishes.
 */
@Component
public class OrphanFileCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OrphanFileCleanupJob.class);

    private final MediaItemRepository mediaItemRepository;
    private final StorageService storageService;

    public OrphanFileCleanupJob(MediaItemRepository mediaItemRepository,
                                StorageService storageService) {
        this.mediaItemRepository = mediaItemRepository;
        this.storageService = storageService;
    }

    /**
     * Runs every hour (fixedDelay = 3600 seconds after the previous run completes).
     * Checks all soft-deleted DB rows and removes their files from disk if still present.
     */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void cleanupOrphanFiles() {
        log.info("OrphanFileCleanupJob started");

        if (!(storageService instanceof LocalStorageService localStorage)) {
            log.info("OrphanFileCleanupJob: non-local storage backend — skipping filesystem cleanup");
            return;
        }

        List<String> deletedFileKeys = mediaItemRepository.findFileKeysOfDeletedItems();
        if (deletedFileKeys.isEmpty()) {
            log.info("OrphanFileCleanupJob: no orphaned files found");
            return;
        }

        int cleaned = 0;
        for (String fileKey : deletedFileKeys) {
            try {
                Path filePath = localStorage.resolveAbsolutePath(fileKey);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.info("OrphanFileCleanupJob: deleted orphaned file '{}'", filePath);
                    cleaned++;
                }
            } catch (IOException e) {
                log.warn("OrphanFileCleanupJob: failed to delete orphaned file '{}': {}", fileKey, e.getMessage());
            }
        }

        log.info("OrphanFileCleanupJob finished — cleaned {} orphaned file(s) out of {}", cleaned, deletedFileKeys.size());
    }
}
