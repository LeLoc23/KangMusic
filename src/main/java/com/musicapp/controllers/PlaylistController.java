package com.musicapp.controllers;

import com.musicapp.models.Playlist;
import com.musicapp.services.PlaylistService;
import com.musicapp.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

@Controller
public class PlaylistController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlaylistController.class);

    private final PlaylistService  playlistService;
    private final UserService      userService;

    public PlaylistController(PlaylistService playlistService, UserService userService) {
        this.playlistService = playlistService;
        this.userService     = userService;
    }

    /** CODE-4 FIX: Uses UserService — no UserRepository needed in this controller. */
    private Long getUserId(Authentication auth) {
        return userService.getUserIdByUsername(auth.getName());
    }

    // ── Danh sách playlist ────────────────────────────────────────────────────
    @GetMapping("/playlists")
    public String listPlaylists() {
        return "redirect:/";
    }

    // ── Chi tiết một playlist ─────────────────────────────────────────────────
    @GetMapping("/playlist/{id}")
    public String viewPlaylistSingular(@PathVariable Long id) {
        return "redirect:/playlists/" + id;
    }

    @GetMapping("/playlists/{id}")
    public String viewPlaylist(@PathVariable Long id,
                               HttpServletRequest request,
                               Authentication auth, Model model) {
        Long userId = getUserId(auth);
        var playlist = playlistService.getPlaylistById(id).orElse(null);
        if (playlist == null) return "redirect:/playlists";

        boolean isOwner = playlist.getUserId().equals(userId);
        // MAJOR-8/S5 FIX: Non-owners cannot access private playlists
        if (!isOwner && !playlist.isPublic()) {
            return "redirect:/?error=access_denied";
        }

        model.addAttribute("playlist",  playlist);
        model.addAttribute("isOwner",   isOwner);
        model.addAttribute("userPlaylists", playlistService.getUserPlaylists(userId));
        return (request.getHeader("HX-Request") != null) ? "playlist :: main-content" : "playlist";
    }

    // ── Tạo playlist (form POST → redirect) ──────────────────────────────────
    @PostMapping("/playlists/create")
    public Object createPlaylist(
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false, defaultValue = "🎵") String coverEmoji,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false, defaultValue = "false") boolean isFolder,
            Authentication auth,
            HttpServletRequest request) {
        Long userId = getUserId(auth);
        Playlist p = playlistService.createPlaylist(userId, name, description, coverEmoji, isFolder, parentId);
        // AJAX request → return JSON
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With")) ||
            request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json")) {
            return ResponseEntity.ok(Map.of("success", true, "id", p.getId(), "name", p.getName()));
        }
        return "redirect:/playlists/" + p.getId();
    }

    // ── Tạo thư mục (dùng playlist với emoji 📁) ──────────────────────────────
    @PostMapping("/playlists/folder/create")
    public String createFolder(
            @RequestParam String name,
            Authentication auth) {
        Long userId = getUserId(auth);
        Playlist p = playlistService.createPlaylist(userId, name, "Thư mục", "📁", true, null);
        return "redirect:/playlists/" + p.getId();
    }

    // ── Thêm bài vào playlist (AJAX/form) ─────────────────────────────────────
    @PostMapping("/playlists/{id}/add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addToPlaylist(
            @PathVariable Long id,
            @RequestParam Long mediaItemId,
            Authentication auth) {
        Long userId = getUserId(auth);
        try {
            boolean added = playlistService.addToPlaylist(id, userId, mediaItemId);
            String msg = added ? "Đã thêm vào playlist" : "Bài đã có trong playlist";
            return ResponseEntity.ok(Map.of("success", true, "message", msg, "added", added));
        } catch (Exception e) {
            log.error("Error adding to playlist", e);
            String errMsg = e.getMessage() != null ? e.getMessage() : "Lỗi hệ thống";
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", errMsg));
        }
    }

    // ── Đổi tên playlist/thư mục ──────────────────────────────────────────────
    @PostMapping("/playlists/{id}/rename")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> renamePlaylist(
            @PathVariable Long id,
            @RequestParam String name,
            Authentication auth,
            HttpServletRequest request) {
        Long userId = getUserId(auth);
        try {
            playlistService.renamePlaylist(id, userId, name);
            if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With")) || 
                (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json"))) {
                return ResponseEntity.ok(Map.of("success", true, "name", name));
            }
            return ResponseEntity.status(302).header("Location", "/playlists/" + id).build();
        } catch (Exception e) {
            log.error("Error renaming playlist", e);
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }
    }

    // ── Xóa item khỏi playlist ────────────────────────────────────────────────
    @PostMapping("/playlists/item/{itemId}/remove")
    public String removeItem(@PathVariable Long itemId, @RequestParam Long playlistId, Authentication auth) {
        Long userId = getUserId(auth);
        playlistService.removeFromPlaylist(itemId, userId);
        return "redirect:/playlists/" + playlistId;
    }

    // ── Xóa playlist ─────────────────────────────────────────────────────────
    @PostMapping("/playlists/{id}/delete")
    public String deletePlaylist(@PathVariable Long id, Authentication auth) {
        Long userId = getUserId(auth);
        playlistService.deletePlaylist(id, userId);
        return "redirect:/playlists";
    }

    // ── API: Danh sách playlist của user (cho modal "Thêm vào playlist") ──────
    @GetMapping("/api/my-playlists")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getMyPlaylists(Authentication auth) {
        if (auth == null) return ResponseEntity.ok(List.of());
        Long userId = getUserId(auth);
        // Trả về TOÀN BỘ playlist (không tính folder) để gắn bài hát
        List<Map<String, Object>> result = playlistService.getAllUserPlaylists(userId).stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "name", p.getName(),
                        "emoji", p.getCoverEmoji()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/playlists/{id}/tracks")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getPlaylistTracks(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = null;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            userId = getUserId(auth);
        }

        var playlistOpt = playlistService.getPlaylistById(id);
        if (playlistOpt.isEmpty() || playlistOpt.get().isFolder()) {
            return ResponseEntity.ok(List.of());
        }

        var playlist = playlistOpt.get();
        boolean isOwner = userId != null && playlist.getUserId().equals(userId);
        if (!isOwner && !playlist.isPublic()) {
            return ResponseEntity.status(403).body(List.of());
        }

        List<Map<String, Object>> tracks = playlist.getItems().stream()
                .map(pi -> pi.getMediaItem())
                .filter(m -> m != null && !m.isDeleted() && m.getFileName() != null)
                .map(m -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", m.getId());
                    row.put("title", m.getTitle());
                    row.put("artist", m.getArtist());
                    row.put("src", "/stream/" + m.getFileName());
                    row.put("type", m.getType() != null ? m.getType().name() : "AUDIO");
                    row.put("genre", m.getGenre());
                    row.put("emotion", m.getEmotionLabel());
                    row.put("lyrics", m.getLyrics());
                    row.put("poster", m.getPosterFilename());
                    return row;
                })
                .toList();

        return ResponseEntity.ok(tracks);
    }

    // ── API: Danh sách thư mục (cho "Di chuyển sang thư mục") ────────────────
    @GetMapping("/api/my-folders")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getMyFolders(Authentication auth) {
        if (auth == null) return ResponseEntity.ok(List.of());
        Long userId = getUserId(auth);
        List<Map<String, Object>> result = playlistService.getUserFolders(userId).stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "name", p.getName(),
                        "emoji", p.getCoverEmoji() != null ? p.getCoverEmoji() : "📁"
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── Di chuyển playlist sang thư mục ──────────────────────────────────────
    @PostMapping("/playlists/{id}/move")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> moveToFolder(
            @PathVariable Long id,
            @RequestParam(required = false) Long folderId,
            Authentication auth) {
        Long userId = getUserId(auth);
        try {
            playlistService.moveToFolder(id, userId, folderId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error moving playlist to folder", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
