package com.musicapp.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "media_items",
    indexes = {
        @Index(name = "idx_media_title",        columnList = "title"),
        @Index(name = "idx_media_artist",       columnList = "artist"),
        @Index(name = "idx_media_type",         columnList = "type"),
        @Index(name = "idx_media_deleted",      columnList = "deleted"),
        @Index(name = "idx_media_emotion",      columnList = "emotion_label"),
        @Index(name = "idx_media_genre",        columnList = "genre"),
        @Index(name = "idx_media_uploaded_at",  columnList = "uploaded_at")
    }
)

public class MediaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 150)
    private String artist;

    @Column(nullable = false, length = 255)
    private String fileName;

    // MAJOR-1 FIX: Enum replaces raw String — EnumType.STRING maps to existing "AUDIO"/"VIDEO" values
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaType type;

    @Column(name = "emotion_label", length = 100)
    private String emotionLabel;

    /** Thể loại nhạc: RELAX, PARTY, CHILL, SAD, ENERGETIC, VIET_POP, INDIE, LOFI */
    @Column(name = "genre", length = 50)
    private String genre;

    /** Thời điểm upload — dùng cho mục "Nhạc mới ra" */
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();

    /** Đếm lượt nghe — dùng cho thuật toán khuyến nghị */
    @Column(name = "play_count", nullable = false)
    private long playCount = 0;

    /** Duration of the audio/video in seconds, populated asynchronously via ID3 tags. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** Soft-delete flag — set to true instead of hard-deleting rows. */
    @Column(nullable = false)
    private boolean deleted = false;

    /** Lời bài hát (văn bản) */
    @Column(name = "lyrics", columnDefinition = "CLOB")
    private String lyrics;

    @Column(name = "poster_filename", length = 255)
    private String posterFilename;

    public MediaItem() {}

    public MediaItem(String title, String artist, String fileName, String type, String emotionLabel) {
        this.title = title;
        this.artist = artist;
        this.fileName = fileName;
        // Accept String for backward compat with seeder; defaults to AUDIO if unknown
        this.type = parseType(type);
        this.emotionLabel = emotionLabel;
    }

    /** Safely parse a type string to enum, defaulting to AUDIO. */
    public static MediaType parseType(String type) {
        try {
            return MediaType.valueOf(type != null ? type.toUpperCase() : "AUDIO");
        } catch (IllegalArgumentException e) {
            return MediaType.AUDIO;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public MediaType getType() { return type; }
    public void setType(MediaType type) { this.type = type; }
    /** String setter for backward compat (e.g. from form params). */
    public void setTypeFromString(String type) { this.type = parseType(type); }

    public String getEmotionLabel() { return emotionLabel; }
    public void setEmotionLabel(String emotionLabel) { this.emotionLabel = emotionLabel; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public long getPlayCount() { return playCount; }
    public void setPlayCount(long playCount) { this.playCount = playCount; }
    /** Use MediaItemRepository.incrementPlayCount() for atomic DB update instead. */
    public void incrementPlayCount() { this.playCount++; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public String getLyrics() { return lyrics; }
    public void setLyrics(String lyrics) { this.lyrics = lyrics; }

    public String getPosterFilename() { return posterFilename; }
    public void setPosterFilename(String posterFilename) { this.posterFilename = posterFilename; }
}