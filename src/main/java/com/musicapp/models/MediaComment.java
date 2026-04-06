package com.musicapp.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "media_comments", indexes = {
    @Index(name = "idx_comment_media", columnList = "media_item_id")
})
public class MediaComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_item_id", nullable = false)
    private MediaItem mediaItem;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "timestamp_seconds")
    private Integer timestampSeconds;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public MediaComment() {}

    public MediaComment(User user, MediaItem mediaItem, String content, Integer timestampSeconds) {
        this.user = user;
        this.mediaItem = mediaItem;
        this.content = content;
        this.timestampSeconds = timestampSeconds;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public MediaItem getMediaItem() { return mediaItem; }
    public String getContent() { return content; }
    public Integer getTimestampSeconds() { return timestampSeconds; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setContent(String content) { this.content = content; }
    public void setTimestampSeconds(Integer timestampSeconds) { this.timestampSeconds = timestampSeconds; }
}
