package com.musicapp.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Lịch sử nghe nhạc — dùng cho Collaborative Filtering.
 * Mỗi lần user phát bài → một bản ghi được tạo.
 */
@Entity
@Table(name = "play_history", indexes = {
    @Index(name = "idx_ph_user",   columnList = "user_id"),
    @Index(name = "idx_ph_media",  columnList = "media_item_id"),
    @Index(name = "idx_ph_played", columnList = "played_at")
})
public class PlayHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;  // null nếu chưa đăng nhập (anonymous)

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_item_id", nullable = false)
    private MediaItem mediaItem;

    @Column(name = "played_at", nullable = false)
    private LocalDateTime playedAt = LocalDateTime.now();

    /** Số giây đã nghe (dùng để tính completion rate về sau). */
    @Column(name = "seconds_listened")
    private Integer secondsListened;

    public PlayHistory() {}

    public PlayHistory(Long userId, MediaItem mediaItem) {
        this.userId    = userId;
        this.mediaItem = mediaItem;
    }

    public Long getId()              { return id; }
    public Long getUserId()          { return userId; }
    public MediaItem getMediaItem()  { return mediaItem; }
    public LocalDateTime getPlayedAt() { return playedAt; }
    public Integer getSecondsListened() { return secondsListened; }
    public void setSecondsListened(Integer s) { this.secondsListened = s; }
}
