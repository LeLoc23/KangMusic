package com.musicapp.repositories;

import com.musicapp.models.MediaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {
    
    // Tìm kiếm nhạc theo phân loại và cảm xúc
    List<MediaItem> findByType(String type);
    List<MediaItem> findByEmotionLabel(String emotionLabel);
}