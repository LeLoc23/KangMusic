package com.musicapp.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "creator_profiles", indexes = {
        @Index(name = "idx_creator_status", columnList = "status"),
        @Index(name = "idx_creator_stage_name", columnList = "stage_name")
})
public class CreatorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "stage_name", nullable = false, length = 150, columnDefinition = "NVARCHAR(150)")
    private String stageName;

    @Column(length = 1000, columnDefinition = "NVARCHAR(1000)")
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "NVARCHAR(20)")
    private CreatorStatus status = CreatorStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by", length = 100, columnDefinition = "NVARCHAR(100)")
    private String reviewedBy;

    @Column(name = "rejection_reason", length = 500, columnDefinition = "NVARCHAR(500)")
    private String rejectionReason;

    public CreatorProfile() {}

    public CreatorProfile(User user, String stageName, String bio) {
        this.user = user;
        this.stageName = stageName;
        this.bio = bio;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public CreatorStatus getStatus() { return status; }
    public void setStatus(CreatorStatus status) { this.status = status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public boolean isPending() { return status == CreatorStatus.PENDING; }
    public boolean isApproved() { return status == CreatorStatus.APPROVED; }
    public boolean isRejected() { return status == CreatorStatus.REJECTED; }
}
