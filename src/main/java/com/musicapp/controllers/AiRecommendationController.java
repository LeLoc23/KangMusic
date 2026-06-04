package com.musicapp.controllers;

import com.musicapp.models.MediaItem;
import com.musicapp.services.MediaService;
import com.musicapp.services.OpenAiService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AiRecommendationController {

    private final MediaService mediaService;
    private final OpenAiService openAiService;

    public AiRecommendationController(MediaService mediaService, OpenAiService openAiService) {
        this.mediaService = mediaService;
        this.openAiService = openAiService;
    }

    @PostMapping("/api/ai/recommend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRecommendation(@RequestParam String query) {
        List<MediaItem> allTracks = mediaService.findAllActiveList();
        Map<String, Object> aiResult = openAiService.recommendMedia(query, allTracks);

        String responseText = (String) aiResult.get("response");
        List<Long> recommendedIds = (List<Long>) aiResult.get("ids");
        if (recommendedIds == null) {
            recommendedIds = new ArrayList<>();
        }

        // Map recommended IDs back to active media items
        final List<Long> finalIds = recommendedIds;
        List<MediaItem> matchedTracks = allTracks.stream()
                .filter(t -> finalIds.contains(t.getId()))
                .toList();

        // Convert to a safe list of map/objects for JS client
        List<Map<String, Object>> trackList = matchedTracks.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", t.getId());
            map.put("title", t.getTitle());
            map.put("artist", t.getCreatorDisplayName());
            map.put("fileName", t.getFileName());
            map.put("posterFilename", t.getPosterFilename());
            map.put("type", t.getType() != null ? t.getType().name() : "AUDIO");
            map.put("genre", t.getGenre());
            map.put("emotionLabel", t.getEmotionLabel());
            map.put("lyrics", t.getLyrics());
            return map;
        }).toList();

        Map<String, Object> body = new HashMap<>();
        body.put("response", responseText);
        body.put("tracks", trackList);

        return ResponseEntity.ok(body);
    }
}
