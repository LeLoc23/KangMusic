package com.musicapp.models;

import jakarta.persistence.*;
import org.hibernate.annotations.Formula;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "playlists", indexes = {
    @Index(name = "idx_playlist_user", columnList = "user_id")
})
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    /** Emoji bìa playlist — VD: 🎵, 🔥, 🌙 */
    @Column(name = "cover_emoji", length = 10)
    private String coverEmoji = "🎵";

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    @Column(name = "is_folder", nullable = false)
    private boolean isFolder = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Playlist parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<Playlist> subPlaylists = new ArrayList<>();

    /** Quan hệ 1-N với PlaylistItem — load LAZY để tránh N+1. */
    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<PlaylistItem> items = new ArrayList<>();

    /** Đếm số bài trực tiếp qua SQL — không cần lazy load. */
    @Formula("(SELECT COUNT(pi.id) FROM playlist_items pi WHERE pi.playlist_id = id)")
    private int itemCount;

    public Playlist() {}
    public Playlist(String name, Long userId) {
        this.name = name;
        this.userId = userId;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverEmoji() { return coverEmoji; }
    public void setCoverEmoji(String coverEmoji) { this.coverEmoji = coverEmoji; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }
    public List<PlaylistItem> getItems() { return items; }
    public int getItemCount() { return itemCount; }

    public boolean isFolder() { return isFolder; }
    public void setFolder(boolean folder) { isFolder = folder; }
    public Playlist getParent() { return parent; }
    public void setParent(Playlist parent) { this.parent = parent; }
    public List<Playlist> getSubPlaylists() { return subPlaylists; }
    public void setSubPlaylists(List<Playlist> subPlaylists) { this.subPlaylists = subPlaylists; }
}
