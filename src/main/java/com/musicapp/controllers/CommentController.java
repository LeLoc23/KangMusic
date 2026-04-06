package com.musicapp.controllers;

import com.musicapp.models.MediaComment;
import com.musicapp.models.MediaItem;
import com.musicapp.models.User;
import com.musicapp.repositories.MediaCommentRepository;
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final MediaCommentRepository commentRepo;
    private final MediaItemRepository mediaItemRepo;
    private final UserService userService;

    public CommentController(MediaCommentRepository commentRepo, MediaItemRepository mediaItemRepo, UserService userService) {
        this.commentRepo = commentRepo;
        this.mediaItemRepo = mediaItemRepo;
        this.userService = userService;
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<List<Map<String, Object>>> getComments(@PathVariable Long mediaId) {
        List<MediaComment> comments = commentRepo.findByMediaItemIdOrderByCreatedAtDesc(mediaId);
        List<Map<String, Object>> response = comments.stream().map(c -> Map.<String, Object>of(
                "id", c.getId(),
                "content", c.getContent(),
                "timestampSeconds", c.getTimestampSeconds() != null ? c.getTimestampSeconds() : -1,
                "createdAt", c.getCreatedAt().toString(),
                "user", Map.of(
                        "username", c.getUser().getUsername(),
                        "displayName", c.getUser().getFullName() != null ? c.getUser().getFullName() : c.getUser().getUsername(),
                        "avatar", ""
                )
        )).collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{mediaId}")
    public ResponseEntity<?> postComment(
            @PathVariable Long mediaId,
            @RequestParam String content,
            @RequestParam(required = false) Integer timestampSeconds,
            Authentication auth) {
        
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));
        }

        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nội dung không được để trống"));
        }

        User user = null;
        try {
            user = userService.getByUsername(auth.getName());
        } catch (Exception e) {
            // ignore
        }
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User không hợp lệ"));
        }

        MediaItem mediaItem = mediaItemRepo.findByIdAndDeletedFalse(mediaId).orElse(null);
        if (mediaItem == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Media item không tồn tại"));
        }

        MediaComment comment = new MediaComment(user, mediaItem, content.trim(), timestampSeconds);
        MediaComment saved = commentRepo.save(comment);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "comment", Map.of(
                        "id", saved.getId(),
                        "content", saved.getContent(),
                        "timestampSeconds", saved.getTimestampSeconds() != null ? saved.getTimestampSeconds() : -1,
                        "user", Map.of(
                                "username", user.getUsername(),
                                "displayName", user.getFullName() != null ? user.getFullName() : user.getUsername(),
                                "avatar", ""
                        )
                )
        ));
    }
}
