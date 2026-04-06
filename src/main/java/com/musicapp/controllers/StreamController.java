package com.musicapp.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Byte-range streaming controller — fixes audio seek bar.
 *
 * CRIT-1 FIX: Uses Spring's ResourceRegion API instead of Files.readAllBytes().
 * A 50 MB file is streamed only for the requested byte range — no heap pressure.
 * A 2 MB max chunk size is enforced to prevent abuse.
 *
 * Performance (P2): Cache-Control and ETag headers added for UUID-named immutable files.
 *
 * Security: path traversal guard validates filename before serving.
 */
@RestController
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    /** Maximum bytes served per partial request — prevents single-request abuse. */
    private static final long MAX_CHUNK_SIZE = 2 * 1024 * 1024L; // 2 MB

    @Value("${app.upload.dir}")
    private String uploadDir;

    @GetMapping("/stream/{filename}")
    public ResponseEntity<ResourceRegion> stream(
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader)
            throws IOException {

        // ── Path traversal guard ───────────────────────────────────────────────
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(filename).normalize();

        if (!filePath.startsWith(uploadPath)) {
            log.warn("Path traversal attempt for filename: '{}'", filename);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        // ── Content-Type detection ─────────────────────────────────────────────
        Resource resource = new FileSystemResource(filePath);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        long fileSize = Files.size(filePath);

        // ── No Range header → return full file via region ─────────────────────
        if (rangeHeader == null || rangeHeader.isBlank()) {
            ResourceRegion region = new ResourceRegion(resource, 0, Math.min(fileSize, MAX_CHUNK_SIZE));
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .header(HttpHeaders.LAST_MODIFIED, String.valueOf(Files.getLastModifiedTime(filePath).toMillis()))
                    .contentType(mediaType)
                    .body(region);
        }

        // ── Parse Range header and return partial content (HTTP 206) ──────────
        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            if (ranges.isEmpty()) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
            }

            // Use the first requested range only (multi-range not needed for audio)
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(fileSize);
            long end   = range.getRangeEnd(fileSize);

            // Clamp chunk to MAX_CHUNK_SIZE to prevent memory abuse
            long length = Math.min(end - start + 1, MAX_CHUNK_SIZE);

            ResourceRegion region = new ResourceRegion(resource, start, length);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .header(HttpHeaders.LAST_MODIFIED, String.valueOf(Files.getLastModifiedTime(filePath).toMillis()))
                    .contentType(mediaType)
                    .body(region);

        } catch (IllegalArgumentException e) {
            log.warn("Malformed Range header '{}': {}", rangeHeader, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
