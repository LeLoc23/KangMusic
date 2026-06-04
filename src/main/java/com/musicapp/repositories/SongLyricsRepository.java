package com.musicapp.repositories;

import com.musicapp.models.SongLyrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SongLyricsRepository extends JpaRepository<SongLyrics, Long> {
    Optional<SongLyrics> findBySongId(Long songId);
    void deleteBySongId(Long songId);
}
