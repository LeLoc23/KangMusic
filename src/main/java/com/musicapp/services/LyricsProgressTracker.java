package com.musicapp.services;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class LyricsProgressTracker {

    public record Progress(int percent, String message) {}

    private final ConcurrentHashMap<Long, Progress> progressBySongId = new ConcurrentHashMap<>();

    public void update(Long songId, int percent, String message) {
        progressBySongId.put(songId, new Progress(
                Math.min(100, Math.max(0, percent)),
                message != null ? message : ""));
    }

    public Progress get(Long songId) {
        return progressBySongId.getOrDefault(songId, new Progress(0, ""));
    }

    public void clear(Long songId) {
        progressBySongId.remove(songId);
    }
}
