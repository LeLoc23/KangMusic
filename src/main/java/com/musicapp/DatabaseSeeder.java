package com.musicapp;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

//@Configuration
//@Profile("dev")
public class DatabaseSeeder {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);
    private static final String SHAPE_FILE = "200663ca-086c-4976-a750-79c9c0096ee5.mp3";
    private static final String CHAY_NGAY_DI_FILE = "1a2b22f0-6cb5-4c58-a087-a6ccd861db6b.mp3";
    private static final String MV_TEST_FILE = "19bef5a7-2440-498e-a427-0788edb02b0c.mp4";

    @Bean
    public CommandLineRunner initDatabase(MediaItemRepository mediaRepo,
                                          CreatorProfileRepository creatorRepo,
                                          UserRepository userRepo,
                                          PasswordEncoder passwordEncoder) {
        return args -> {
            String adminUsername = System.getenv().getOrDefault("ADMIN_USERNAME", "admin");
            String adminPassword = System.getenv().getOrDefault("ADMIN_PASSWORD", "admin123A!");
            String adminEmail = System.getenv().getOrDefault("ADMIN_EMAIL", "admin@kangmusic.local");

            if (!userRepo.existsByUsername(adminUsername)) {
                log.info("Seeding default admin account...");
                User adminAccount = new User(
                        adminUsername,
                        passwordEncoder.encode(adminPassword),
                        adminEmail,
                        "KangMusic Admin",
                        "ROLE_ADMIN"
                );
                userRepo.save(adminAccount);
            }

            CreatorProfile edSheeran = seedCreator(userRepo, creatorRepo, passwordEncoder, "edsheeran", "creator123A!", "Ed Sheeran");
            CreatorProfile sonTung = seedCreator(userRepo, creatorRepo, passwordEncoder, "sontungmtp", "creator123A!", "Sơn Tùng M-TP");
            CreatorProfile indieArtist = seedCreator(userRepo, creatorRepo, passwordEncoder, "indieartist", "creator123A!", "Nghệ sĩ Indie");

            if (mediaRepo.count() == 0) {
                log.info("Seeding sample media data...");
                seedMedia(mediaRepo, "Shape of You", SHAPE_FILE, "AUDIO", "Vui vẻ", edSheeran);
                seedMedia(mediaRepo, "Chạy Ngay Đi", CHAY_NGAY_DI_FILE, "AUDIO", "Sôi động", sonTung);
                seedMedia(mediaRepo, "MV Test Nhạc", MV_TEST_FILE, "VIDEO", "Chill", indieArtist);
            } else {
                syncSeedMedia(mediaRepo, "Shape of You", SHAPE_FILE, "shape-of-you.mp3", "AUDIO", "Vui vẻ", edSheeran);
                syncSeedMedia(mediaRepo, "Chạy Ngay Đi", CHAY_NGAY_DI_FILE, "chay-ngay-di.mp3", "AUDIO", "Sôi động", sonTung);
                syncSeedMedia(mediaRepo, "MV Test Nhạc", MV_TEST_FILE, "video-test.mp4", "VIDEO", "Chill", indieArtist);
            }
        };
    }

    private CreatorProfile seedCreator(UserRepository userRepo,
                                       CreatorProfileRepository creatorRepo,
                                       PasswordEncoder passwordEncoder,
                                       String username,
                                       String password,
                                       String stageName) {
        User user = userRepo.findByUsername(username).orElseGet(() -> {
            User created = new User(username, passwordEncoder.encode(password),
                    username + "@kangmusic.local", stageName, "ROLE_CREATOR");
            return userRepo.save(created);
        });
        user.setFullName(stageName);
        user.setRole("ROLE_CREATOR");
        userRepo.save(user);

        CreatorProfile profile = creatorRepo.findByUserId(user.getId())
                .orElseGet(() -> new CreatorProfile(user, stageName, "Seed creator for demo data"));
        profile.setStageName(stageName);
        profile.setBio("Seed creator for demo data");
        profile.setStatus(CreatorStatus.APPROVED);
        return creatorRepo.save(profile);
    }

    private void seedMedia(MediaItemRepository mediaRepo,
                           String title,
                           String fileName,
                           String type,
                           String emotionLabel,
                           CreatorProfile creator) {
        MediaItem item = new MediaItem(title, creator.getStageName(), fileName, type, emotionLabel);
        item.setCreators(java.util.List.of(creator));
        mediaRepo.save(item);
    }

    private void syncSeedMedia(MediaItemRepository mediaRepo,
                               String title,
                               String fileName,
                               String legacyFileName,
                               String type,
                               String emotionLabel,
                               CreatorProfile creator) {
        mediaRepo.findAll().stream()
                .filter(item -> fileName.equals(item.getFileName()) || legacyFileName.equals(item.getFileName()))
                .forEach(item -> {
                    item.setTitle(title);
                    item.setFileName(fileName);
                    item.setType(MediaItem.parseType(type));
                    item.setEmotionLabel(emotionLabel);
                    item.setArtist(creator.getStageName());
                    item.setCreators(java.util.List.of(creator));
                    mediaRepo.save(item);
                });
    }
}
