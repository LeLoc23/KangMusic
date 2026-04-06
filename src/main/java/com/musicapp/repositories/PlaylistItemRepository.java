package com.musicapp.repositories;

import com.musicapp.models.PlaylistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, Long> {

    /** Tìm bài trong playlist theo media ID. */
    @Query("SELECT pi FROM PlaylistItem pi WHERE pi.playlist.id = :playlistId AND pi.mediaItem.id = :mediaId")
    Optional<PlaylistItem> findByPlaylistAndMedia(@Param("playlistId") Long playlistId,
                                                   @Param("mediaId") Long mediaId);

    /** Vị trí lớn nhất hiện tại trong playlist (dùng để append). */
    @Query("SELECT COALESCE(MAX(pi.position), -1) FROM PlaylistItem pi WHERE pi.playlist.id = :playlistId")
    int findMaxPosition(@Param("playlistId") Long playlistId);

    boolean existsByPlaylistIdAndMediaItemId(Long playlistId, Long mediaItemId);
}
