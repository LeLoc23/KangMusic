package com.musicapp.controllers;

import com.musicapp.repositories.UserRepository;
import com.musicapp.services.LibraryService;
import com.musicapp.services.PlaylistService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

/**
 * CRIT-5 FIX:
 * - @ControllerAdvice(annotations = Controller.class) — only fires on Thymeleaf
 *   web controllers, NOT on @RestController API endpoints.
 *   This eliminates the 2 DB queries that ran on every single API call.
 */
@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAdvice {

    private final UserRepository userRepo;
    private final PlaylistService playlistService;
    private final LibraryService libraryService;

    public GlobalModelAdvice(UserRepository userRepo, PlaylistService playlistService, LibraryService libraryService) {
        this.userRepo = userRepo;
        this.playlistService = playlistService;
        this.libraryService = libraryService;
    }

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            userRepo.findByUsername(auth.getName()).ifPresent(u -> {
                model.addAttribute("globalPlaylists", playlistService.getUserPlaylists(u.getId()));
                model.addAttribute("globalLikedCount", libraryService.getLikedIds(u.getId()).size());
            });
        }
    }
}
