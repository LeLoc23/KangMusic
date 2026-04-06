package com.musicapp.repositories;

import com.musicapp.models.MediaItem;
import com.musicapp.models.PlayHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PlayHistoryRepository extends JpaRepository<PlayHistory, Long> {

    /**
     * Lấy các bài user đã nghe gần đây — dùng làm "seed" cho recommendation.
     */
    @Query("SELECT ph.mediaItem FROM PlayHistory ph WHERE ph.userId = :userId ORDER BY ph.playedAt DESC")
    List<MediaItem> findRecentlyPlayedByUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * Collaborative Filtering: tìm bài mà các user KHÁC cũng nghe,
     * sau khi đã nghe bài có mediaItemId.
     *
     * Thuật toán (Item-based CF thuần JPQL):
     *  1. Tìm những userId đã nghe :mediaItemId
     *  2. Tìm bài KHÁC mà những userId đó cũng nghe
     *  3. Đếm số user chung (score)
     *  4. Sắp xếp giảm dần theo score
     */
    @Query("""
           SELECT ph2.mediaItem, COUNT(DISTINCT ph2.userId) AS score
           FROM PlayHistory ph1
           JOIN PlayHistory ph2 ON ph1.userId = ph2.userId
           WHERE ph1.mediaItem.id = :mediaItemId
             AND ph2.mediaItem.id != :mediaItemId
             AND ph2.userId IS NOT NULL
             AND ph2.mediaItem.deleted = false
           GROUP BY ph2.mediaItem
           ORDER BY score DESC
           """)
    List<Object[]> findCollaborativeRecommendations(@Param("mediaItemId") Long mediaItemId,
                                                     Pageable pageable);

    /**
     * Multi-seed CF: tìm bài phù hợp dựa trên NHIỀU bài seed (lịch sử gần đây của user).
     * Trả về [(MediaItem, score)] sắp theo score DESC.
     */
    @Query("""
           SELECT ph2.mediaItem, COUNT(DISTINCT ph2.userId) AS score
           FROM PlayHistory ph1
           JOIN PlayHistory ph2 ON ph1.userId = ph2.userId
           WHERE ph1.mediaItem.id IN :seedIds
             AND ph2.mediaItem.id NOT IN :seedIds
             AND ph2.userId != :userId
             AND ph2.mediaItem.deleted = false
           GROUP BY ph2.mediaItem
           ORDER BY score DESC
           """)
    List<Object[]> findRecommendationsFromSeeds(@Param("userId") Long userId,
                                                 @Param("seedIds") List<Long> seedIds,
                                                 Pageable pageable);

    /** Bài được nghe nhiều nhất toàn hệ thống (fallback khi không có history). */
    @Query("""
           SELECT ph.mediaItem, COUNT(ph.id) AS plays
           FROM PlayHistory ph
           WHERE ph.mediaItem.deleted = false
           GROUP BY ph.mediaItem
           ORDER BY plays DESC
           """)
    List<Object[]> findGlobalTrending(Pageable pageable);

    /** Xóa lịch sử cũ hơn N ngày để tránh bảng phình to. */
    @Modifying
    @Query("DELETE FROM PlayHistory ph WHERE ph.playedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
