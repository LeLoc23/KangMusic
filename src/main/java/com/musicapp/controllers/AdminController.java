package com.musicapp.controllers;

import com.musicapp.services.MediaService;
import com.musicapp.services.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * M1 FIX: Delegates all business logic to UserService and MediaService.
 * M2 FIX: Role validation handled inside UserService (whitelist enforced).
 * M3 FIX: deleteMedia delegates to MediaService which deletes physical file first.
 * I1 FIX: Constructor injection.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final MediaService mediaService;

    public AdminController(UserService userService, MediaService mediaService) {
        this.userService = userService;
        this.mediaService = mediaService;
    }

    @GetMapping
    public String adminDashboard(Model model, Principal principal) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("mediaItems", mediaService.findAllForAdmin());
        model.addAttribute("currentUsername", principal.getName());
        return "admin";
    }

    // ================= USER MANAGEMENT =================

    @PostMapping("/user/delete")
    public String deleteUser(@RequestParam Long id, Principal principal) {
        userService.deleteUser(id, principal.getName());
        return "redirect:/admin";
    }

    @PostMapping("/user/lock")
    public String toggleLockUser(@RequestParam Long id, Principal principal) {
        userService.toggleLock(id, principal.getName());
        return "redirect:/admin";
    }

    /**
     * M2 FIX: Role is validated against a strict whitelist inside UserService.
     * Invalid roles are rejected with an error redirect.
     */
    @PostMapping("/user/role")
    public String changeUserRole(@RequestParam Long id,
                                  @RequestParam String newRole,
                                  Principal principal) {
        try {
            userService.changeRole(id, newRole, principal.getName());
        } catch (IllegalArgumentException e) {
            return "redirect:/admin?error=invalid_role";
        }
        return "redirect:/admin";
    }

    // ================= MEDIA MANAGEMENT =================

    /**
     * M3 FIX: Physical file is deleted from disk before DB record is removed.
     */
    @PostMapping("/media/delete")
    public String deleteMedia(@RequestParam Long id) {
        mediaService.deleteMedia(id);
        return "redirect:/admin";
    }
}