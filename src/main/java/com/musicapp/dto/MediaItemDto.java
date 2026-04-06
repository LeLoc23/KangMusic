package com.musicapp.dto;

/**
 * S3/S4 FIX: Data Transfer Object for MediaItem API responses.
 * Prevents leaking internal fields: fileName (storage key), deleted flag.
 * The 'src' field is the public streaming URL (/stream/{uuid}).
 */
public record MediaItemDto(
        Long id,
        String title,
        String artist,
        String src,           // /stream/{uuid} — never expose raw fileName
        String posterSrc,     // /stream/{posterFilename} or null
        String type,          // "AUDIO" or "VIDEO"
        String genre,
        String emotionLabel,
        long playCount,
        Integer durationSeconds
) {
    /** Convenience factory from a MediaItem entity. */
    public static MediaItemDto from(com.musicapp.models.MediaItem m) {
        String src = m.getFileName() != null ? "/stream/" + m.getFileName() : null;
        String posterSrc = m.getPosterFilename() != null ? "/stream/" + m.getPosterFilename() : null;
        return new MediaItemDto(
                m.getId(),
                m.getTitle(),
                m.getArtist(),
                src,
                posterSrc,
                m.getType() != null ? m.getType().name() : com.musicapp.models.MediaType.AUDIO.name(),
                m.getGenre(),
                m.getEmotionLabel(),
                m.getPlayCount(),
                m.getDurationSeconds()
        );
    }
}
