package com.musicapp.controllers;

import com.musicapp.dto.MediaItemDto;
import com.musicapp.models.CreatorProfile;
import com.musicapp.services.LibraryService;
import com.musicapp.services.MediaService;
import com.musicapp.services.CreatorService;
import com.musicapp.services.OpenAiService;
import com.musicapp.services.PlaylistService;
import com.musicapp.services.RecommendationService;
import com.musicapp.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.ArrayList;

/**
 * CODE-2: Removed dead commented-out import.
 * CODE-4: Uses UserService.getUserIdByUsername() — no more UserRepository here.
 * MAJOR-5: Upload IOException now logs properly and redirects with error param.
 * S3/S4: /api/search and /api/genre/** now return MediaItemDto, not raw MediaItem.
 */
@Controller
public class MusicController {

    private static final Logger log = LoggerFactory.getLogger(MusicController.class);

    private final MediaService            mediaService;
    private final RecommendationService   recommendationService;
    private final LibraryService          libraryService;
    private final PlaylistService         playlistService;
    private final UserService             userService;
    private final CreatorService          creatorService;
    private final OpenAiService           openAiService;

    public MusicController(MediaService mediaService,
                           RecommendationService recommendationService,
                           LibraryService libraryService,
                           PlaylistService playlistService,
                           UserService userService,
                           CreatorService creatorService,
                           OpenAiService openAiService) {
        this.mediaService          = mediaService;
        this.recommendationService = recommendationService;
        this.libraryService        = libraryService;
        this.playlistService       = playlistService;
        this.userService           = userService;
        this.creatorService        = creatorService;
        this.openAiService         = openAiService;
    }

    /** CODE-4: Centralised via UserService — no UserRepository needed here. */
    private Long resolveUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        try {
            return userService.getUserIdByUsername(auth.getName());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasAuthority(Authentication auth, String authority) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    // ── Trang chủ ─────────────────────────────────────────────────────────────
    @GetMapping("/")
    public String homePage(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "") String genre,
            @RequestParam(required = false, defaultValue = "0") int page,
            HttpServletRequest request,
            Authentication auth,
            Model model) {

        if (page < 0) page = 0;
        Long userId = resolveUserId(auth);

        var mediaPage = mediaService.findPaginated(query, genre, PageRequest.of(page, MediaService.PAGE_SIZE));

        model.addAttribute("mediaList",       mediaPage.getContent());
        model.addAttribute("query",           query);
        model.addAttribute("selectedGenre",   genre);
        model.addAttribute("currentPage",     mediaPage.getNumber());
        model.addAttribute("totalPages",      mediaPage.getTotalPages());
        model.addAttribute("hasPrev",         mediaPage.hasPrevious());
        model.addAttribute("hasNext",         mediaPage.hasNext());

        // Section "Nhạc mới ra"
        model.addAttribute("newReleases", mediaService.findNewReleases(10));

        // Section "Đề xuất hôm nay" — bài phổ biến nhất
        model.addAttribute("todayPicks", mediaService.findPaginated("", null,
                PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("playCount").descending()))
                .getContent());

        // Liked IDs — để highlight nút ♥ trên card
        if (userId != null) {
            model.addAttribute("likedIds", libraryService.getLikedIds(userId));
            model.addAttribute("searchPlaylists", playlistService.getAllUserPlaylists(userId));
        }

        return (request.getHeader("HX-Request") != null) ? "index :: main-content" : "index";
    }

    // ── Upload form ───────────────────────────────────────────────────────────
    @GetMapping("/add")
    public String showAddForm(Authentication auth, Model model) {
        boolean isAdmin = hasAuthority(auth, "ROLE_ADMIN");
        CreatorProfile currentCreator = null;
        if (!isAdmin) {
            currentCreator = creatorService.findApprovedByUsername(auth.getName()).orElse(null);
            if (currentCreator == null) {
                return "redirect:/profile?error=creator_not_approved";
            }
        }
        model.addAttribute("approvedCreators", creatorService.getApprovedCreators());
        model.addAttribute("isAdminUpload", isAdmin);
        model.addAttribute("currentCreator", currentCreator);
        return "add";
    }

    @PostMapping("/add")
    public String saveMedia(
            @RequestParam String title,
            @RequestParam(required = false, defaultValue = "") String artist,
            @RequestParam("mediaFile") MultipartFile file,
            @RequestParam(value = "posterFile", required = false) MultipartFile posterFile,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String album,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String emotionLabel,
            @RequestParam(required = false) Integer durationSeconds,
            @RequestParam(required = false) String lyrics,
            @RequestParam(required = false) List<Long> creatorIds,
            Authentication auth) {

        try {
            boolean isAdmin = hasAuthority(auth, "ROLE_ADMIN");
            Long uploaderUserId = resolveUserId(auth);
            List<Long> finalCreatorIds = collectCreatorIds(auth, isAdmin, creatorIds);
            String finalType = (type != null) ? type : "AUDIO";
            mediaService.saveMedia(title, artist, file, posterFile, finalType, emotionLabel,
                    durationSeconds, genre, album, lyrics, uploaderUserId, isAdmin, finalCreatorIds);
        } catch (java.io.IOException e) {
            // MAJOR-5 FIX: Log the error and redirect with an error message instead of silently swallowing
            log.error("Upload failed for title='{}' artist='{}': {}", title, artist, e.getMessage(), e);
            return "redirect:/add?error=upload_failed";
        } catch (IllegalArgumentException e) {
            log.warn("Invalid upload request for title='{}': {}", title, e.getMessage());
            return "redirect:/add?error=" + e.getMessage();
        }
        return "redirect:/";
    }

    @GetMapping("/media/{id}/edit")
    public String editMediaForm(@PathVariable Long id, Authentication auth, Model model) {
        var item = mediaService.findByIdForManagement(id);
        if (item == null) return "redirect:/";
        boolean isAdmin = hasAuthority(auth, "ROLE_ADMIN");
        Long userId = resolveUserId(auth);
        if (!isAdmin && (item.getUploadedByUserId() == null || !item.getUploadedByUserId().equals(userId))) {
            return "redirect:/?error=access_denied";
        }
        model.addAttribute("media", item);
        model.addAttribute("approvedCreators", creatorService.getApprovedCreators());
        model.addAttribute("isAdminUpload", isAdmin);
        model.addAttribute("currentCreator", creatorService.findApprovedByUsername(auth.getName()).orElse(null));
        return "edit-media";
    }

    @PostMapping("/media/{id}/edit")
    public String updateMedia(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam(required = false, defaultValue = "") String artist,
            @RequestParam(value = "posterFile", required = false) MultipartFile posterFile,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String album,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String emotionLabel,
            @RequestParam(required = false) Integer durationSeconds,
            @RequestParam(required = false) String lyrics,
            @RequestParam(required = false) List<Long> creatorIds,
            Authentication auth) {
        try {
            boolean isAdmin = hasAuthority(auth, "ROLE_ADMIN");
            Long userId = resolveUserId(auth);
            mediaService.updateMedia(id, title, artist, posterFile, type, emotionLabel, durationSeconds,
                    genre, album, lyrics, isAdmin, userId, collectCreatorIds(auth, isAdmin, creatorIds));
            return isAdmin ? "redirect:/admin" : "redirect:/creator";
        } catch (Exception e) {
            log.warn("Update media failed id={}: {}", id, e.getMessage());
            return "redirect:/media/" + id + "/edit?error=true";
        }
    }

    // ── Ghi nhận lượt nghe ────────────────────────────────────────────────────
    @PostMapping("/api/play/{id}")
    @ResponseBody
    public ResponseEntity<Void> recordPlay(@PathVariable Long id, Authentication auth) {
        recommendationService.recordPlay(resolveUserId(auth), id);
        return ResponseEntity.ok().build();
    }

    // ── API: Tìm kiếm gợi ý — S3/S4 FIX: returns DTO, not raw entity ──────────
    @GetMapping("/api/search")
    @ResponseBody
    public List<MediaItemDto> searchSuggestions(@RequestParam String q) {
        return mediaService.findPaginated(q, org.springframework.data.domain.PageRequest.of(0, 5))
                .getContent().stream().map(MediaItemDto::from).toList();
    }

    // ── API lọc nhạc theo thể loại — S3/S4 FIX: returns DTO ──────────────────
    @GetMapping("/api/genre/{genre}")
    @ResponseBody
    public ResponseEntity<List<MediaItemDto>> getByGenre(
            @PathVariable String genre,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        List<MediaItemDto> dtos = mediaService.findByGenre(genre, limit)
                .stream().map(MediaItemDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    // ── API bài tương tự (cho auto-fill queue) ────────────────────────────────
    @GetMapping("/api/similar/{id}")
    @ResponseBody
    public ResponseEntity<List<MediaItemDto>> getSimilar(
            @PathVariable Long id,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String emotion,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        var item = mediaService.findById(id);
        String g = (genre != null && !genre.isBlank()) ? genre : (item != null ? item.getGenre() : null);
        String e = (emotion != null && !emotion.isBlank()) ? emotion : (item != null ? item.getEmotionLabel() : null);
        List<MediaItemDto> dtos = mediaService.findSimilar(id, g, e, limit)
                .stream().map(MediaItemDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    // ── Trang nghệ sĩ (Spotify style) ──────────────────────────────────────────
    @GetMapping("/artist/{id}")
    public String artistDetail(@PathVariable Long id,
                               HttpServletRequest request,
                               Authentication auth,
                               Model model) {
        CreatorProfile creator = creatorService.findById(id).orElse(null);
        if (creator == null || !creator.isApproved()) {
            return "redirect:/";
        }

        List<com.musicapp.models.MediaItem> mediaList = mediaService.findByCreatorIdActive(id);

        model.addAttribute("artist", creator);
        model.addAttribute("mediaList", mediaList);

        Long userId = resolveUserId(auth);
        if (userId != null) {
            model.addAttribute("likedIds", libraryService.getLikedIds(userId));
            model.addAttribute("searchPlaylists", playlistService.getAllUserPlaylists(userId));
        }

        return (request.getHeader("HX-Request") != null) ? "artist :: main-content" : "artist";
    }

    // ── Trang chi tiết bài hát ────────────────────────────────────────────────
    @GetMapping("/track/{id}")
    public String trackDetail(@PathVariable Long id,
                              HttpServletRequest request,
                              Authentication auth, Model model) {
        var item = mediaService.findById(id);
        if (item == null) return "redirect:/";

        Long userId = resolveUserId(auth);

        // Bài tương tự — dùng làm "Nghe tiếp"
        var similar = mediaService.findSimilar(id, item.getGenre(), item.getEmotionLabel(), 8);

        model.addAttribute("track",   item);
        model.addAttribute("similar", similar);
        model.addAttribute("liked", userId != null && libraryService.isLiked(userId, id));

        // Recommendations logic with fallback
        List<com.musicapp.models.MediaItem> recommendations = recommendationService.getRecommendations(userId, 12);
        if (recommendations == null || recommendations.isEmpty()) {
            recommendations = mediaService.findNewReleases(12);
        }
        recommendations = openAiService.rerankRecommendations(item, recommendations, 12);
        model.addAttribute("recommendations", recommendations);

        return (request.getHeader("HX-Request") != null) ? "track :: main-content" : "track";
    }

    private List<Long> collectCreatorIds(Authentication auth, boolean isAdmin, List<Long> selectedIds) {
        List<Long> ids = new ArrayList<>();
        if (!isAdmin) {
            CreatorProfile self = creatorService.findApprovedByUsername(auth.getName())
                    .orElseThrow(() -> new IllegalArgumentException("creator_not_approved"));
            ids.add(self.getId());
        }
        if (selectedIds != null) {
            ids.addAll(selectedIds);
        }
        return ids;
    }
}
