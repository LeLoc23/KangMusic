package com.musicapp.services;

import com.musicapp.models.CreatorProfile;
import com.musicapp.models.CreatorStatus;
import com.musicapp.models.User;
import com.musicapp.repositories.CreatorProfileRepository;
import com.musicapp.repositories.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CreatorService {

    private final CreatorProfileRepository creatorRepo;
    private final UserRepository userRepo;

    public CreatorService(CreatorProfileRepository creatorRepo, UserRepository userRepo) {
        this.creatorRepo = creatorRepo;
        this.userRepo = userRepo;
    }

    public CreatorProfile requestCreator(String username, String stageName, String bio) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        String cleanStageName = stageName != null ? stageName.trim() : "";
        if (cleanStageName.isBlank()) {
            cleanStageName = user.getFullName() != null && !user.getFullName().isBlank()
                    ? user.getFullName().trim()
                    : user.getUsername();
        }

        Optional<CreatorProfile> existing = creatorRepo.findByUserId(user.getId());
        if (existing.isPresent()) {
            CreatorProfile profile = existing.get();
            if (profile.getStatus() == CreatorStatus.APPROVED || profile.getStatus() == CreatorStatus.PENDING) {
                return profile;
            }
            profile.setStageName(cleanStageName);
            profile.setBio(bio);
            profile.setStatus(CreatorStatus.PENDING);
            profile.setRequestedAt(LocalDateTime.now());
            profile.setReviewedAt(null);
            profile.setReviewedBy(null);
            profile.setRejectionReason(null);
            return creatorRepo.save(profile);
        }

        return creatorRepo.save(new CreatorProfile(user, cleanStageName, bio));
    }

    public void approve(Long profileId, String adminUsername) {
        creatorRepo.findById(profileId).ifPresent(profile -> {
            profile.setStatus(CreatorStatus.APPROVED);
            profile.setReviewedAt(LocalDateTime.now());
            profile.setReviewedBy(adminUsername);
            profile.setRejectionReason(null);
            profile.getUser().setRole("ROLE_CREATOR");
            userRepo.save(profile.getUser());
            creatorRepo.save(profile);
        });
    }

    public void approveUserAsCreator(Long userId, String adminUsername) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        CreatorProfile profile = creatorRepo.findByUserId(userId)
                .orElseGet(() -> new CreatorProfile(user, defaultStageName(user), "Approved directly by admin"));
        profile.setStatus(CreatorStatus.APPROVED);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setReviewedBy(adminUsername);
        profile.setRejectionReason(null);
        user.setRole("ROLE_CREATOR");
        userRepo.save(user);
        creatorRepo.save(profile);
    }

    public void demoteCreator(Long userId, String adminUsername) {
        creatorRepo.findByUserId(userId).ifPresent(profile -> {
            profile.setStatus(CreatorStatus.REJECTED);
            profile.setReviewedAt(LocalDateTime.now());
            profile.setReviewedBy(adminUsername);
            profile.setRejectionReason("Role changed by admin");
            creatorRepo.save(profile);
        });
    }

    public void reject(Long profileId, String reason, String adminUsername) {
        creatorRepo.findById(profileId).ifPresent(profile -> {
            profile.setStatus(CreatorStatus.REJECTED);
            profile.setReviewedAt(LocalDateTime.now());
            profile.setReviewedBy(adminUsername);
            profile.setRejectionReason(reason);
            if ("ROLE_CREATOR".equals(profile.getUser().getRole())) {
                profile.getUser().setRole("ROLE_USER");
                userRepo.save(profile.getUser());
            }
            creatorRepo.save(profile);
        });
    }

    @Transactional(readOnly = true)
    public Optional<CreatorProfile> findById(Long id) {
        return creatorRepo.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<CreatorProfile> findByUsername(String username) {
        return creatorRepo.findByUserUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<CreatorProfile> findApprovedByUsername(String username) {
        return findByUsername(username).filter(CreatorProfile::isApproved);
    }

    @Transactional(readOnly = true)
    public List<CreatorProfile> getPendingRequests() {
        return creatorRepo.findByStatusOrderByRequestedAtAsc(CreatorStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<CreatorProfile> getApprovedCreators() {
        return creatorRepo.findByStatusOrderByStageNameAsc(CreatorStatus.APPROVED);
    }

    private String defaultStageName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName().trim()
                : user.getUsername();
    }
}
