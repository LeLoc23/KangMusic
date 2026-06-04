package com.musicapp.controllers;

import com.musicapp.services.CreatorService;
import com.musicapp.services.MediaService;
import com.musicapp.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CreatorController {

    private final CreatorService creatorService;
    private final MediaService mediaService;
    private final UserService userService;

    public CreatorController(CreatorService creatorService, MediaService mediaService, UserService userService) {
        this.creatorService = creatorService;
        this.mediaService = mediaService;
        this.userService = userService;
    }

    @GetMapping("/creator")
    public String creatorDashboard(Authentication auth, HttpServletRequest request, Model model) {
        var profile = creatorService.findApprovedByUsername(auth.getName()).orElse(null);
        if (profile == null) {
            return "redirect:/profile?error=creator_not_approved";
        }
        Long userId = userService.getUserIdByUsername(auth.getName());
        model.addAttribute("creatorProfile", profile);
        model.addAttribute("creatorMedia", mediaService.findByUploader(userId));
        return (request.getHeader("HX-Request") != null) ? "creator :: main-content" : "creator";
    }
}
