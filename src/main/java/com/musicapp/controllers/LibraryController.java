package com.musicapp.controllers;

import com.musicapp.services.LibraryService;
import com.musicapp.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Controller
public class LibraryController {

    private final LibraryService libraryService;
    private final UserService    userService;

    public LibraryController(LibraryService libraryService, UserService userService) {
        this.libraryService = libraryService;
        this.userService    = userService;
    }

    /** CODE-4 FIX: Uses UserService — no UserRepository needed here. */
    private Long getUserId(Authentication auth) {
        return userService.getUserIdByUsername(auth.getName());
    }

    // ── Trang thư viện yêu thích ──────────────────────────────────────────────
    @GetMapping("/library")
    public String libraryPage(HttpServletRequest request,
                              Authentication auth, Model model) {
        Long userId = getUserId(auth);
        model.addAttribute("libraryItems", libraryService.getUserLibrary(userId));
        return (request.getHeader("HX-Request") != null) ? "library :: main-content" : "library";
    }

    // ── Toggle yêu thích (AJAX) ───────────────────────────────────────────────
    @PostMapping("/api/library/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleLike(
            @RequestParam Long mediaItemId,
            Authentication auth) {

        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));
        }

        Long userId = getUserId(auth);
        boolean liked = libraryService.toggleLibrary(userId, mediaItemId);
        return ResponseEntity.ok(Map.of(
                "liked", liked,
                "message", liked ? "Đã thêm vào thư viện ❤️" : "Đã xóa khỏi thư viện"
        ));
    }

    // ── Kiểm tra trạng thái liked (dùng để highlight tim) ─────────────────────
    @GetMapping("/api/library/check/{mediaItemId}")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkLiked(
            @PathVariable Long mediaItemId, Authentication auth) {
        boolean liked = auth != null && libraryService.isLiked(getUserId(auth), mediaItemId);
        return ResponseEntity.ok(Map.of("liked", liked));
    }

    // ── Gỡ bài hát khỏi thư viện (form POST → redirect) ─────────────────────────
    @PostMapping("/library/remove")
    public String removeFromLibrary(@RequestParam Long mediaItemId, Authentication auth) {
        if (auth != null) {
            libraryService.toggleLibrary(getUserId(auth), mediaItemId);
        }
        return "redirect:/library";
    }
}
