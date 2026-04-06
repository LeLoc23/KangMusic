package com.musicapp.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_library", indexes = {
    @Index(name = "idx_lib_user",  columnList = "user_id"),
    @Index(name = "idx_lib_media", columnList = "media_item_id"),
    @Index(name = "idx_lib_unique", columnList = "user_id, media_item_id", unique = true)
})
public class UserLibrary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "media_item_id", nullable = false)
    private MediaItem mediaItem;

    @Column(name = "added_at")
    private LocalDateTime addedAt = LocalDateTime.now();

    public UserLibrary() {}
    public UserLibrary(Long userId, MediaItem mediaItem) {
        this.userId    = userId;
        this.mediaItem = mediaItem;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public MediaItem getMediaItem() { return mediaItem; }
    public LocalDateTime getAddedAt() { return addedAt; }
}
