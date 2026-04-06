package com.musicapp.repositories;

import com.musicapp.models.MediaComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaCommentRepository extends JpaRepository<MediaComment, Long> {

    List<MediaComment> findByMediaItemIdOrderByCreatedAtDesc(Long mediaItemId);
    
}
