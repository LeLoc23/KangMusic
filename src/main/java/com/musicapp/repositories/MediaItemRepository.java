package com.musicapp.repositories;

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

    // ── Lookup ────────────────────────────────────────────────────────────────
    Optional<MediaItem> findByIdAndDeletedFalse(Long id);

    // ── Tìm kiếm có phân trang (hỗ trợ filter genre) ─────────────────────────
    @Query("""
           SELECT m FROM MediaItem m
           WHERE m.deleted = false
             AND (:query IS NULL OR :query = ''
                  OR LOWER(m.title)  LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(m.artist) LIKE LOWER(CONCAT('%', :query, '%')))
             AND (:genre IS NULL OR :genre = '' OR m.genre = :genre)
           ORDER BY m.id DESC
           """)
    Page<MediaItem> searchActive(@Param("query") String query,
                                 @Param("genre") String genre,
                                 Pageable pageable);

    // ── Overload không genre (tương thích ngược) ──────────────────────────────
    @Query("""
           SELECT m FROM MediaItem m
           WHERE m.deleted = false
             AND (:query IS NULL OR :query = ''
                  OR LOWER(m.title)  LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(m.artist) LIKE LOWER(CONCAT('%', :query, '%')))
           ORDER BY m.id DESC
           """)
    Page<MediaItem> searchActive(@Param("query") String query, Pageable pageable);

    // ── Nhạc mới ra (sắp xếp theo uploadedAt DESC) ────────────────────────────
    @Query("SELECT m FROM MediaItem m WHERE m.deleted = false ORDER BY m.uploadedAt DESC")
    Page<MediaItem> findNewReleases(Pageable pageable);

    // ── Theo thể loại ─────────────────────────────────────────────────────────
    @Query("SELECT m FROM MediaItem m WHERE m.deleted = false AND m.genre = :genre ORDER BY m.playCount DESC")
    Page<MediaItem> findByGenreActive(@Param("genre") String genre, Pageable pageable);

    // ── Bài tương tự (cùng genre hoặc emotionLabel, loại trừ bài hiện tại) ────
    @Query("""
           SELECT m FROM MediaItem m
           WHERE m.deleted = false
             AND m.id != :excludeId
             AND ((:genre IS NOT NULL AND m.genre = :genre)
                  OR (:emotionLabel IS NOT NULL AND m.emotionLabel = :emotionLabel))
           ORDER BY m.playCount DESC
           """)
    Page<MediaItem> findSimilar(@Param("excludeId") Long excludeId,
                                @Param("genre") String genre,
                                @Param("emotionLabel") String emotionLabel,
                                Pageable pageable);

    // ── Đề xuất hôm nay (phổ biến nhất) ─────────────────────────────────────
    @Query("SELECT m FROM MediaItem m WHERE m.deleted = false ORDER BY m.playCount DESC")
    Page<MediaItem> findTopByPlayCount(Pageable pageable);

    // ── Legacy ────────────────────────────────────────────────────────────────
    List<MediaItem> findByTypeAndDeletedFalse(String type);
    List<MediaItem> findByEmotionLabelAndDeletedFalse(String emotionLabel);

    // ── Admin ─────────────────────────────────────────────────────────────────
    @Query("SELECT m FROM MediaItem m WHERE m.deleted = false ORDER BY m.id DESC")
    Page<MediaItem> findAllActive(Pageable pageable);

    // ── Atomic play count increment (MAJOR-6 FIX) ─────────────────────────────
    /** Atomically increments play_count — no read-then-write race condition. */
    @Modifying
    @Transactional
    @Query("UPDATE MediaItem m SET m.playCount = m.playCount + 1 WHERE m.id = :id AND m.deleted = false")
    int incrementPlayCount(@Param("id") Long id);

    // ── Orphan cleanup ────────────────────────────────────────────────────────
    @Query("SELECT m.fileName FROM MediaItem m WHERE m.deleted = true")
    List<String> findFileKeysOfDeletedItems();
}