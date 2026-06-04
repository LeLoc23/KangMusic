package com.musicapp.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "song_lyrics", indexes = {
    @Index(name = "idx_lyrics_song_id", columnList = "song_id")
})
public class SongLyrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "song_id", nullable = false, unique = true)
    private Long songId;

    @Lob
    @Column(name = "lyrics_text", columnDefinition = "NVARCHAR(MAX)")
    private String lyricsText;

    @Lob
    @Column(name = "lyrics_json", columnDefinition = "NVARCHAR(MAX)")
    private String lyricsJson;

    @Column(length = 50, columnDefinition = "NVARCHAR(50)")
    private String format;

    @Column(name = "generated_by", length = 100, columnDefinition = "NVARCHAR(100)")
    private String generatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public SongLyrics() {}

    public SongLyrics(Long songId, String lyricsText, String lyricsJson, String format, String generatedBy) {
        this.songId = songId;
        this.lyricsText = lyricsText;
        this.lyricsJson = lyricsJson;
        this.format = format;
        this.generatedBy = generatedBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSongId() { return songId; }
    public void setSongId(Long songId) { this.songId = songId; }

    public String getLyricsText() { return lyricsText; }
    public void setLyricsText(String lyricsText) { this.lyricsText = lyricsText; }

    public String getLyricsJson() { return lyricsJson; }
    public void setLyricsJson(String lyricsJson) { this.lyricsJson = lyricsJson; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
