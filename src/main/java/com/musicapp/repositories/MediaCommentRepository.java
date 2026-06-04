package com.musicapp.repositories;

import com.musicapp.models.MediaComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaCommentRepository extends JpaRepository<MediaComment, Long> {

    List<MediaComment> findByMediaItemIdOrderByCreatedAtDesc(Long mediaItemId);

    @Modifying
    @Query("DELETE FROM MediaComment c WHERE c.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
    
}
