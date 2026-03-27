package com.musicapp.models;

import jakarta.persistence.*;

@Entity
@Table(name = "media_items")
public class MediaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String artist;
    private String fileName;
    private String type;         // "AUDIO" hoặc "VIDEO"
    private String emotionLabel; // Nhãn cảm xúc (Vui, buồn, chill...)

    // Constructor rỗng (Bắt buộc phải có cho Spring Boot)
    public MediaItem() {}

    // Constructor có tham số
    public MediaItem(String title, String artist, String fileName, String type, String emotionLabel) {
        this.title = title;
        this.artist = artist;
        this.fileName = fileName;
        this.type = type;
        this.emotionLabel = emotionLabel;
    }

    // --- CÁC HÀM GETTER VÀ SETTER ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getEmotionLabel() { return emotionLabel; }
    public void setEmotionLabel(String emotionLabel) { this.emotionLabel = emotionLabel; }
}