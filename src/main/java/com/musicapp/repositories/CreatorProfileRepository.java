package com.musicapp.repositories;

import com.musicapp.models.CreatorProfile;
import com.musicapp.models.CreatorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, Long> {
    Optional<CreatorProfile> findByUserId(Long userId);
    Optional<CreatorProfile> findByUserUsername(String username);
    List<CreatorProfile> findByStatusOrderByRequestedAtAsc(CreatorStatus status);
    List<CreatorProfile> findByStatusOrderByStageNameAsc(CreatorStatus status);
    boolean existsByUserId(Long userId);
}
