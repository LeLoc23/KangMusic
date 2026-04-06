package com.musicapp.models;

/**
 * MAJOR-1 FIX: Replaces raw String type field on MediaItem.
 * Stored as STRING in DB so existing "AUDIO"/"VIDEO" values map correctly.
 * JSON serialization outputs "AUDIO"/"VIDEO" — backward-compatible with JS.
 */
public enum MediaType {
    AUDIO, VIDEO
}
