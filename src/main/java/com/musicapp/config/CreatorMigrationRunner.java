package com.musicapp.config;

import com.musicapp.models.CreatorProfile;
import com.musicapp.models.CreatorStatus;
import com.musicapp.models.MediaItem;
import com.musicapp.models.User;
import com.musicapp.repositories.CreatorProfileRepository;
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// @Configuration
@Profile("dev")
public class CreatorMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CreatorMigrationRunner.class);

    private final MediaItemRepository mediaItemRepo;
    private final CreatorProfileRepository creatorRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public CreatorMigrationRunner(MediaItemRepository mediaItemRepo,
                                  CreatorProfileRepository creatorRepo,
                                  UserRepository userRepo,
                                  PasswordEncoder passwordEncoder) {
        this.mediaItemRepo = mediaItemRepo;
        this.creatorRepo = creatorRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Starting Creator Migration Runner - checking for existing artists...");
        
        List<MediaItem> allItems = mediaItemRepo.findAll();
        for (MediaItem item : allItems) {
            String artistName = item.getArtist();
            if (artistName == null || artistName.trim().isEmpty() || "Unknown Artist".equalsIgnoreCase(artistName.trim())) {
                continue;
            }
            
            // Clean up name for a username (e.g. lowercase, remove spaces)
            String cleanName = artistName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            if (cleanName.isEmpty()) {
                cleanName = "artist_" + item.getId();
            }
            
            // 1. Ensure CreatorProfile exists for the artist stage name
            Optional<CreatorProfile> profileOpt = creatorRepo.findByStatusOrderByStageNameAsc(CreatorStatus.APPROVED)
                    .stream()
                    .filter(p -> p.getStageName().equalsIgnoreCase(artistName.trim()))
                    .findFirst();
                    
            CreatorProfile creatorProfile;
            if (profileOpt.isPresent()) {
                creatorProfile = profileOpt.get();
            } else {
                log.info("Creator Profile not found for artist: '{}'. Creating one...", artistName);
                
                // Ensure unique username
                String finalUsername = cleanName;
                int counter = 1;
                while (userRepo.existsByUsername(finalUsername)) {
                    finalUsername = cleanName + counter;
                    counter++;
                }
                
                User user = new User();
                user.setUsername(finalUsername);
                user.setPassword(passwordEncoder.encode("creator123A!"));
                user.setEmail(finalUsername + "@kangmusic.local");
                user.setFullName(artistName);
                user.setRole("ROLE_CREATOR");
                userRepo.save(user);
                
                creatorProfile = new CreatorProfile();
                creatorProfile.setUser(user);
                creatorProfile.setStageName(artistName.trim());
                creatorProfile.setBio("Auto-generated creator profile for artist " + artistName);
                creatorProfile.setStatus(CreatorStatus.APPROVED);
                creatorProfile.setRequestedAt(LocalDateTime.now());
                creatorProfile.setReviewedAt(LocalDateTime.now());
                creatorProfile.setReviewedBy("system");
                creatorRepo.save(creatorProfile);
            }
            
            // 2. Link item with this creator profile
            if (item.getCreators() == null || !item.getCreators().contains(creatorProfile)) {
                log.info("Linking track '{}' (ID: {}) with creator '{}'", item.getTitle(), item.getId(), creatorProfile.getStageName());
                if (item.getCreators() == null) {
                    item.setCreators(new java.util.ArrayList<>());
                }
                item.getCreators().add(creatorProfile);
                mediaItemRepo.save(item);
            }
        }
        log.info("Creator Migration Runner completed successfully.");
    }
}
