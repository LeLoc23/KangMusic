package com.musicapp.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        @Index(name = "idx_media_album",        columnList = "album"),
        @Index(name = "idx_media_uploaded_at",  columnList = "uploaded_at"),
        @Index(name = "idx_media_approval",     columnList = "approval_status"),
        @Index(name = "idx_media_uploader",     columnList = "uploaded_by_user_id")
    }
)
public class MediaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150, columnDefinition = "NVARCHAR(150)")
    private String title;

    @Column(nullable = false, length = 150, columnDefinition = "NVARCHAR(150)")
    private String artist;

    @Column(nullable = false, length = 255, columnDefinition = "NVARCHAR(255)")
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "NVARCHAR(20)")
    private MediaType type;

    @Column(name = "emotion_label", length = 100, columnDefinition = "NVARCHAR(100)")
    private String emotionLabel;

    @Column(name = "genre", length = 50, columnDefinition = "NVARCHAR(50)")
    private String genre;

    @Column(name = "album", length = 150, columnDefinition = "NVARCHAR(150)")
    private String album;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "play_count", nullable = false)
    private long playCount = 0;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20, columnDefinition = "NVARCHAR(20) DEFAULT 'APPROVED'")
    private MediaApprovalStatus approvalStatus = MediaApprovalStatus.APPROVED;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(nullable = false)
    private boolean deleted = false;

    @Lob
    @Column(name = "lyrics", columnDefinition = "NVARCHAR(MAX)")
    private String lyrics;

    @Column(name = "poster_filename", length = 255, columnDefinition = "NVARCHAR(255)")
    private String posterFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "lyrics_status", nullable = false, length = 20, columnDefinition = "NVARCHAR(20) DEFAULT 'NONE'")
    private LyricsStatus lyricsStatus = LyricsStatus.NONE;

    @ManyToMany
    @JoinTable(
            name = "media_creators",
            joinColumns = @JoinColumn(name = "media_item_id"),
            inverseJoinColumns = @JoinColumn(name = "creator_profile_id")
    )
    private List<CreatorProfile> creators = new ArrayList<>();

    public MediaItem() {}

    public MediaItem(String title, String artist, String fileName, String type, String emotionLabel) {
        this.title = title;
        this.artist = artist;
        this.fileName = fileName;
        this.type = parseType(type);
        this.emotionLabel = emotionLabel;
    }

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
    public void setTypeFromString(String type) { this.type = parseType(type); }

    public String getEmotionLabel() { return emotionLabel; }
    public void setEmotionLabel(String emotionLabel) { this.emotionLabel = emotionLabel; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public long getPlayCount() { return playCount; }
    public void setPlayCount(long playCount) { this.playCount = playCount; }
    public void incrementPlayCount() { this.playCount++; }

    public Long getUploadedByUserId() { return uploadedByUserId; }
    public void setUploadedByUserId(Long uploadedByUserId) { this.uploadedByUserId = uploadedByUserId; }

    public MediaApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(MediaApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }
    public boolean isPendingApproval() { return approvalStatus == MediaApprovalStatus.PENDING; }
    public boolean isApproved() { return approvalStatus == MediaApprovalStatus.APPROVED; }
    public boolean isRejected() { return approvalStatus == MediaApprovalStatus.REJECTED; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public String getLyrics() { return lyrics; }
    public void setLyrics(String lyrics) { this.lyrics = lyrics; }

    public String getPosterFilename() { return posterFilename; }
    public void setPosterFilename(String posterFilename) { this.posterFilename = posterFilename; }

    public List<CreatorProfile> getCreators() { return creators; }
    public void setCreators(List<CreatorProfile> creators) {
        this.creators = creators != null ? creators : new ArrayList<>();
        if (!this.creators.isEmpty()) {
            this.artist = getCreatorDisplayName();
        }
    }

    public String getCreatorDisplayName() {
        if (creators == null || creators.isEmpty()) return artist;
        return creators.stream()
                .map(CreatorProfile::getStageName)
                .collect(Collectors.joining(", "));
    }

    public LyricsStatus getLyricsStatus() { return lyricsStatus; }
    public void setLyricsStatus(LyricsStatus lyricsStatus) { this.lyricsStatus = lyricsStatus; }
}
