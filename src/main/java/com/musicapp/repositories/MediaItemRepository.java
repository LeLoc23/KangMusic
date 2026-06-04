package com.musicapp.repositories;

import com.musicapp.models.MediaApprovalStatus;
import com.musicapp.models.MediaItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    Optional<MediaItem> findByIdAndDeletedFalse(Long id);

    @Query("""
           SELECT DISTINCT m FROM MediaItem m
           LEFT JOIN m.creators c
           WHERE m.deleted = false
             AND m.approvalStatus = com.musicapp.models.MediaApprovalStatus.APPROVED
             AND (:query IS NULL OR :query = ''
                  OR LOWER(m.title)  LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(m.artist) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(m.album)  LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(c.stageName) LIKE LOWER(CONCAT('%', :query, '%')))
             AND (:genre IS NULL OR :genre = '' OR m.genre = :genre)
           ORDER BY m.id DESC
           """)
    Page<MediaItem> searchActive(@Param("query") String query,
                                 @Param("genre") String genre,
                                 Pageable pageable);

    @Query("""
           SELECT DISTINCT m FROM MediaItem m
           LEFT JOIN m.creators c
           WHERE m.deleted = false
             AND m.approvalStatus = com.musicapp.models.MediaApprovalStatus.APPROVED
             AND (:query IS NULL OR :query = ''
                  OR LOWER(m.title)  LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(m.artist) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(m.album)  LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(c.stageName) LIKE LOWER(CONCAT('%', :query, '%')))
           ORDER BY m.id DESC
           """)
    Page<MediaItem> searchActive(@Param("query") String query, Pageable pageable);

    @Query("""
           SELECT m FROM MediaItem m
           WHERE m.deleted = false
             AND m.approvalStatus = com.musicapp.models.MediaApprovalStatus.APPROVED
           ORDER BY m.uploadedAt DESC
           """)
    Page<MediaItem> findNewReleases(Pageable pageable);

    @Query("""
           SELECT m FROM MediaItem m
           WHERE m.deleted = false
             AND m.approvalStatus = com.musicapp.models.MediaApprovalStatus.APPROVED
             AND m.genre = :genre
           ORDER BY m.playCount DESC
           """)
    Page<MediaItem> findByGenreActive(@Param("genre") String genre, Pageable pageable);

    @Query("""
           SELECT m FROM MediaItem m
           WHERE m.deleted = false
             AND m.approvalStatus = com.musicapp.models.MediaApprovalStatus.APPROVED
             AND m.id != :excludeId
             AND ((:genre IS NOT NULL AND m.genre = :genre)
                  OR (:emotionLabel IS NOT NULL AND m.emotionLabel = :emotionLabel))
           ORDER BY m.playCount DESC
           """)
    Page<MediaItem> findSimilar(@Param("excludeId") Long excludeId,
                                @Param("genre") String genre,
                                @Param("emotionLabel") String emotionLabel,
                                Pageable pageable);

    @Query("""
           SELECT m FROM MediaItem m
           WHERE m.deleted = false
             AND m.approvalStatus = com.musicapp.models.MediaApprovalStatus.APPROVED
           ORDER BY m.playCount DESC
           """)
    Page<MediaItem> findTopByPlayCount(Pageable pageable);

    List<MediaItem> findByTypeAndDeletedFalse(String type);
    List<MediaItem> findByEmotionLabelAndDeletedFalse(String emotionLabel);

    @Query("SELECT m FROM MediaItem m WHERE m.deleted = false AND m.approvalStatus = com.musicapp.models.MediaApprovalStatus.APPROVED ORDER BY m.id DESC")
    List<MediaItem> findAllActiveList();

    @Query("SELECT m FROM MediaItem m WHERE m.deleted = false ORDER BY m.id DESC")
    Page<MediaItem> findAllActive(Pageable pageable);

    List<MediaItem> findByApprovalStatusAndDeletedFalseOrderByUploadedAtAsc(MediaApprovalStatus approvalStatus);

    List<MediaItem> findByUploadedByUserIdAndDeletedFalseOrderByUploadedAtDesc(Long uploadedByUserId);

    @Query("""
           SELECT m FROM MediaItem m
           JOIN m.creators c
           WHERE c.id = :creatorId
             AND m.deleted = false
             AND m.approvalStatus = com.musicapp.models.MediaApprovalStatus.APPROVED
           ORDER BY m.playCount DESC
           """)
    List<MediaItem> findByCreatorIdActive(@Param("creatorId") Long creatorId);

    @Query("""
           SELECT DISTINCT m FROM MediaItem m
           LEFT JOIN m.creators c
           WHERE (m.uploadedByUserId = :userId OR c.id = :creatorId)
             AND m.deleted = false
           ORDER BY m.uploadedAt DESC
           """)
    List<MediaItem> findByCreatorOrUploader(@Param("creatorId") Long creatorId, @Param("userId") Long userId);

    @Query("""
           SELECT DISTINCT m FROM MediaItem m
           JOIN m.creators c
           WHERE c.id = :creatorId
           """)
    List<MediaItem> findAllLinkedToCreator(@Param("creatorId") Long creatorId);

    @Modifying
    @Query("UPDATE MediaItem m SET m.uploadedByUserId = null WHERE m.uploadedByUserId = :userId")
    int clearUploaderByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE MediaItem m SET m.playCount = m.playCount + 1 WHERE m.id = :id AND m.deleted = false AND m.approvalStatus = com.musicapp.models.MediaApprovalStatus.APPROVED")
    int incrementPlayCount(@Param("id") Long id);

    Optional<MediaItem> findByFileNameAndDeletedFalse(String fileName);

    Optional<MediaItem> findByPosterFilenameAndDeletedFalse(String posterFilename);

    @Query("SELECT m.fileName FROM MediaItem m WHERE m.deleted = true")
    List<String> findFileKeysOfDeletedItems();
}
