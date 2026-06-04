package com.musicapp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicapp.models.LyricsStatus;
import com.musicapp.models.MediaItem;
import com.musicapp.models.SongLyrics;
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.repositories.SongLyricsRepository;
import com.musicapp.services.LyricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Controller
public class AdminLyricsController {

    private static final Logger log = LoggerFactory.getLogger(AdminLyricsController.class);

    private final MediaItemRepository mediaItemRepository;
    private final SongLyricsRepository songLyricsRepository;
    private final LyricsService lyricsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminLyricsController(MediaItemRepository mediaItemRepository,
                                 SongLyricsRepository songLyricsRepository,
                                 LyricsService lyricsService) {
        this.mediaItemRepository = mediaItemRepository;
        this.songLyricsRepository = songLyricsRepository;
        this.lyricsService = lyricsService;
    }

    @GetMapping("/admin/songs/{songId}/lyrics/edit")
    public String editLyricsForm(@PathVariable Long songId, Model model) {
        Optional<MediaItem> songOpt = mediaItemRepository.findById(songId);
        if (songOpt.isEmpty()) {
            return "redirect:/admin?error=song_not_found";
        }
        MediaItem song = songOpt.get();
        model.addAttribute("song", song);

        Optional<SongLyrics> lyricsOpt = songLyricsRepository.findBySongId(songId);
        String lyricsJsonStr = "[]";

        if (lyricsOpt.isPresent()) {
            lyricsJsonStr = lyricsOpt.get().getLyricsJson();
        } else {
            // Generate default segments from raw lyrics if present
            String rawLyrics = song.getLyrics();
            if (rawLyrics != null && !rawLyrics.trim().isEmpty()) {
                try {
                    List<Map<String, Object>> defaultSegments = new ArrayList<>();
                    String[] lines = rawLyrics.split("\n");
                    double start = 0.0;
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        // Skip headers like [Verse] or [Chorus]
                        if (line.startsWith("[") && line.endsWith("]")) continue;

                        Map<String, Object> seg = new LinkedHashMap<>();
                        seg.put("line", line);
                        seg.put("start", start);
                        seg.put("end", start + 4.0);
                        defaultSegments.add(seg);
                        start += 5.0;
                    }
                    lyricsJsonStr = objectMapper.writeValueAsString(defaultSegments);
                } catch (Exception e) {
                    log.error("Failed to generate default lyrics segments for songId={}", songId, e);
                }
            }
        }

        model.addAttribute("lyricsJson", lyricsJsonStr);
        return "admin-lyrics-edit";
    }

    @PostMapping("/admin/songs/{songId}/lyrics/save")
    public String saveLyrics(@PathVariable Long songId,
                             @RequestParam String lyricsJson,
                             @RequestParam String lyricsText) {
        Optional<MediaItem> songOpt = mediaItemRepository.findById(songId);
        if (songOpt.isEmpty()) {
            return "redirect:/admin?error=song_not_found";
        }
        MediaItem song = songOpt.get();

        try {
            Optional<SongLyrics> existingLyricsOpt = songLyricsRepository.findBySongId(songId);
            SongLyrics songLyrics;
            if (existingLyricsOpt.isPresent()) {
                songLyrics = existingLyricsOpt.get();
                songLyrics.setLyricsText(lyricsText);
                songLyrics.setLyricsJson(lyricsJson);
                songLyrics.setFormat("json");
                songLyrics.setGeneratedBy("Admin Manual Edit");
                songLyrics.setUpdatedAt(LocalDateTime.now());
            } else {
                songLyrics = new SongLyrics(songId, lyricsText, lyricsJson, "json", "Admin Manual Edit");
            }
            songLyricsRepository.save(songLyrics);

            song.setLyricsStatus(LyricsStatus.MANUAL_EDITED);
            song.setLyrics(lyricsText);
            mediaItemRepository.save(song);

            log.info("Saved manually edited lyrics for songId={}", songId);
            return "redirect:/admin/songs/" + songId + "/lyrics/edit?success=true";

        } catch (Exception e) {
            log.error("Failed to save edited lyrics for songId={}", songId, e);
            return "redirect:/admin/songs/" + songId + "/lyrics/edit?error=save_failed";
        }
    }

    @PostMapping("/admin/songs/{songId}/lyrics/generate")
    public String generateLyrics(@PathVariable Long songId) {
        Optional<MediaItem> songOpt = mediaItemRepository.findById(songId);
        if (songOpt.isEmpty()) {
            return "redirect:/admin?error=song_not_found";
        }
        lyricsService.generateLyricsAsync(songId);
        return "redirect:/admin/songs/" + songId + "/lyrics/edit?generating=true";
    }

    // ================= CLIENT API ENDPOINTS =================

    @GetMapping(value = "/api/songs/{songId}/lyrics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<String> getSongLyrics(@PathVariable Long songId) {
        Optional<SongLyrics> lyricsOpt = songLyricsRepository.findBySongId(songId);
        if (lyricsOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("[]");
        }
        return ResponseEntity.ok(lyricsOpt.get().getLyricsJson());
    }

    @GetMapping(value = "/api/songs/{songId}/lyrics/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> getLyricsStatus(@PathVariable Long songId) {
        Optional<MediaItem> songOpt = mediaItemRepository.findById(songId);
        if (songOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Song not found"));
        }
        return ResponseEntity.ok(Map.of("status", songOpt.get().getLyricsStatus().name()));
    }
}
