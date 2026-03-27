package com.musicapp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.musicapp.models.MediaItem;
import com.musicapp.models.User;
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.repositories.UserRepository;

@Configuration
public class DatabaseSeeder {

    @Bean
    public CommandLineRunner initDatabase(MediaItemRepository mediaRepo, UserRepository userRepo, PasswordEncoder passwordEncoder) {
        return args -> {
            
            // 1. TẠO DỮ LIỆU NHẠC MẪU
            if (mediaRepo.count() == 0) {
                System.out.println("Dang tao du lieu am nhac mau...");
                mediaRepo.save(new MediaItem("Shape of You", "Ed Sheeran", "shape-of-you.mp3", "AUDIO", "Vui vẻ"));
                mediaRepo.save(new MediaItem("Chạy Ngay Đi", "Sơn Tùng M-TP", "chay-ngay-di.mp3", "AUDIO", "Sôi động"));
                mediaRepo.save(new MediaItem("MV Test Nhạc", "Nghệ sĩ Indie", "video-test.mp4", "VIDEO", "Chill"));
            }

            // 2. TẠO TÀI KHOẢN ADMIN MẶC ĐỊNH
            if (userRepo.count() == 0) {
                System.out.println("Dang tao tai khoan Admin mac dinh...");
                
                User adminAccount = new User("admin", passwordEncoder.encode("admin123"), "leloc558@gmail.com", "Lê Tấn Lộc (Admin)", "ROLE_ADMIN");
                
                userRepo.save(adminAccount);
            }
        };
    }
}