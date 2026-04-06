package com.musicapp.repositories;

import com.musicapp.models.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Playlist> findByUserIdAndParentNullOrderByCreatedAtDesc(Long userId);

    /** Eagerly load subPlaylists to avoid LazyInitializationException in Thymeleaf */
    @Query("SELECT DISTINCT p FROM Playlist p LEFT JOIN FETCH p.subPlaylists s WHERE p.userId = :userId AND p.parent IS NULL ORDER BY p.createdAt DESC")
    List<Playlist> findRootPlaylistsWithChildren(@Param("userId") Long userId);

    List<Playlist> findByUserIdAndIsFolderTrue(Long userId);

    /** CRIT-3 FIX: Proper indexed query — no full-table scan. */
    List<Playlist> findByUserIdAndIsFolderFalseOrderByCreatedAtDesc(Long userId);

    Optional<Playlist> findByIdAndUserId(Long id, Long userId);

    /** Đếm số bài trong playlist (tránh load toàn bộ items). */
    @Query("SELECT COUNT(pi) FROM PlaylistItem pi WHERE pi.playlist.id = :playlistId")
    int countItems(@Param("playlistId") Long playlistId);
}
