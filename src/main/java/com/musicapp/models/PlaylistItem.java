package com.musicapp.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "playlist_items", indexes = {
    @Index(name = "idx_pi_playlist", columnList = "playlist_id"),
    @Index(name = "idx_pi_media",    columnList = "media_item_id")
})
public class PlaylistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "media_item_id", nullable = false)
    private MediaItem mediaItem;

    @Column(nullable = false)
    private int position = 0;

    @Column(name = "added_at")
    private LocalDateTime addedAt = LocalDateTime.now();

    public PlaylistItem() {}
    public PlaylistItem(Playlist playlist, MediaItem mediaItem, int position) {
        this.playlist  = playlist;
        this.mediaItem = mediaItem;
        this.position  = position;
    }

    public Long getId() { return id; }
    public Playlist getPlaylist() { return playlist; }
    public void setPlaylist(Playlist playlist) { this.playlist = playlist; }
    public MediaItem getMediaItem() { return mediaItem; }
    public void setMediaItem(MediaItem mediaItem) { this.mediaItem = mediaItem; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public LocalDateTime getAddedAt() { return addedAt; }
}
