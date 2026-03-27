package com.musicapp.controllers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping; // Hỗ trợ nhận file
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.musicapp.models.MediaItem;
import com.musicapp.repositories.MediaItemRepository;

@Controller
public class MusicController {

    @Autowired
    private MediaItemRepository mediaItemRepository;

    @GetMapping("/")
    public String homePage(Model model) {
        List<MediaItem> playlist = mediaItemRepository.findAll();
        model.addAttribute("mediaList", playlist);
        return "index"; 
    }

    @GetMapping("/add")
    public String showAddForm() {
        return "add"; 
    }

    // NÂNG CẤP: Xử lý Upload File
    @PostMapping("/add")
    public String saveMedia(
            @RequestParam String title,
            @RequestParam String artist,
            @RequestParam("mediaFile") MultipartFile file, // Nhận file từ giao diện
            @RequestParam String type,
            @RequestParam String emotionLabel) {
        
        try {
            // Lấy tên file gốc
            String originalFileName = file.getOriginalFilename();

            // Xác định đường dẫn để lưu file vào mục
            String uploadDir = System.getProperty("user.dir") + "/src/main/resources/static/media/";
            
            // Tạo thư mục nếu nó chưa tồn tại
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs(); 
            }

            Path path = Paths.get(uploadDir + originalFileName);
            Files.write(path, file.getBytes());

            // Lưu thông tin
            MediaItem newItem = new MediaItem(title, artist, originalFileName, type, emotionLabel);
            mediaItemRepository.save(newItem);

        } catch (Exception e) {
            System.out.println("Lỗi khi tải file: " + e.getMessage());
        }
        
        return "redirect:/"; 
    }
}