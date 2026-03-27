package com.musicapp.controllers;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.musicapp.models.User; 
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.repositories.UserRepository;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MediaItemRepository mediaItemRepository;

    // 1. TRUYỀN TÊN NGƯỜI ĐANG ĐĂNG NHẬP SANG GIAO DIỆN
    @GetMapping
    public String adminDashboard(Model model, Principal principal) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("mediaItems", mediaItemRepository.findAll());
        
        model.addAttribute("currentUsername", principal.getName()); 
        
        return "admin"; 
    }

    // ================= XỬ LÝ NGƯỜI DÙNG =================

    // Xóa người dùng 
    @PostMapping("/user/delete")
    public String deleteUser(@RequestParam Long id, Principal principal) {
        User targetUser = userRepository.findById(id).orElse(null);
        
        if (targetUser != null && !targetUser.getUsername().equals(principal.getName())) {
            userRepository.deleteById(id);
        }
        return "redirect:/admin";
    }

    // Khóa / Mở khóa người dùng 
    @PostMapping("/user/lock")
    public String toggleLockUser(@RequestParam Long id, Principal principal) {
        User targetUser = userRepository.findById(id).orElse(null);
        
        if (targetUser != null && !targetUser.getUsername().equals(principal.getName())) {
            targetUser.setLocked(!targetUser.isLocked());
            userRepository.save(targetUser);
        }
        return "redirect:/admin";
    }

    // Đổi quyền
    @PostMapping("/user/role")
    public String changeUserRole(@RequestParam Long id, @RequestParam String newRole, Principal principal) {
        User targetUser = userRepository.findById(id).orElse(null);
        
        if (targetUser != null && !targetUser.getUsername().equals(principal.getName())) {
            targetUser.setRole(newRole);
            userRepository.save(targetUser);
        }
        return "redirect:/admin";
    }

    // ================= XỬ LÝ BÀI HÁT =================
    @PostMapping("/media/delete")
    public String deleteMedia(@RequestParam Long id) {
        mediaItemRepository.deleteById(id);
        return "redirect:/admin";
    }
}