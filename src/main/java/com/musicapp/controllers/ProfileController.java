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
import com.musicapp.repositories.UserRepository;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private UserRepository userRepository;

    // Hiển thị trang Hồ sơ
    @GetMapping
    public String profilePage(Model model, Principal principal) {
        User user = userRepository.findByUsername(principal.getName());
        model.addAttribute("user", user);
        return "profile";
    }

    // Xử lý đổi mật khẩu
    @PostMapping("/change-password")
    public String changePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Principal principal) {

        User user = userRepository.findByUsername(principal.getName());

        // Kiểm tra mật khẩu cũ 
        if (!user.getPassword().equals(oldPassword)) {
            return "redirect:/profile?error=old_wrong";
        }

        // Check xem mk mới có đúng với ráng buộc không
        if (!newPassword.matches("^(?=.*[A-Z]).{6,}$")) {
            return "redirect:/profile?error=weak_pass";
        }

        // Check xem pass đã giống nhau chưa
        if (!newPassword.equals(confirmPassword)) {
            return "redirect:/profile?error=mismatch";
        }

        user.setPassword(newPassword);
        userRepository.save(user);

        return "redirect:/profile?success=true";
    }
}