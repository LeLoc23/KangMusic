package com.musicapp.repositories;

import com.musicapp.models.UserLibrary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserLibraryRepository extends JpaRepository<UserLibrary, Long> {

    List<UserLibrary> findByUserIdOrderByAddedAtDesc(Long userId);

    Optional<UserLibrary> findByUserIdAndMediaItemId(Long userId, Long mediaItemId);

    boolean existsByUserIdAndMediaItemId(Long userId, Long mediaItemId);

    /** Lấy tập hợp mediaItemId đã yêu thích — dùng để highlight nút ♡ trên card. */
    @Query("SELECT ul.mediaItem.id FROM UserLibrary ul WHERE ul.userId = :userId")
    Set<Long> findLikedMediaIds(@Param("userId") Long userId);
}
