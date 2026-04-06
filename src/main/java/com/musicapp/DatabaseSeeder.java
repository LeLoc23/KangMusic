package com.musicapp;

import com.musicapp.models.MediaItem;
import com.musicapp.models.User;
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * C5 FIX:
 * - @Profile("dev") ensures seeder NEVER runs in production
 * - Admin credentials read from environment variables (no hardcoded real emails)
 * - existsByUsername used to prevent race-condition duplicate-insert on startup
 */
@Configuration
@Profile("dev")
public class DatabaseSeeder {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    @Bean
    public CommandLineRunner initDatabase(MediaItemRepository mediaRepo,
                                          UserRepository userRepo,
                                          PasswordEncoder passwordEncoder) {
        return args -> {

            // C5 FIX: credentials from env vars, with safe fallback for local dev only
            String adminUsername = System.getenv().getOrDefault("ADMIN_USERNAME", "admin");
            String adminPassword = System.getenv().getOrDefault("ADMIN_PASSWORD", "admin123");
            String adminEmail    = System.getenv().getOrDefault("ADMIN_EMAIL",    "admin@kangmusic.local");

            // 1. Seed sample media
            if (mediaRepo.count() == 0) {
                log.info("Seeding sample media data...");
                mediaRepo.save(new MediaItem("Shape of You", "Ed Sheeran", "shape-of-you.mp3", "AUDIO", "Vui vẻ"));
                mediaRepo.save(new MediaItem("Chạy Ngay Đi", "Sơn Tùng M-TP", "chay-ngay-di.mp3", "AUDIO", "Sôi động"));
                mediaRepo.save(new MediaItem("MV Test Nhạc", "Nghệ sĩ Indie", "video-test.mp4", "VIDEO", "Chill"));
            }

            // 2. Seed or update admin account
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
                log.info("Admin account created. Username: {}", adminUsername);
            } else {
                // Force update password to BCrypt to fix legacy plaintext accounts
                userRepo.findByUsername(adminUsername).ifPresent(admin -> {
                    if (!admin.getPassword().startsWith("$2a$")) {
                        log.info("Upgrading legacy plaintext admin password to BCrypt...");
                        admin.setPassword(passwordEncoder.encode(adminPassword));
                        userRepo.save(admin);
                    }
                });
            }
        };
    }
}