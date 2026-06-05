package com.musicapp.controllers;

import com.musicapp.services.MediaService;
import com.musicapp.services.OpenAiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final MediaService mediaService;
    private final OpenAiService openAiService;

    public AiController(MediaService mediaService, OpenAiService openAiService) {
        this.mediaService = mediaService;
        this.openAiService = openAiService;
    }

    @PostMapping("/chat/{mediaId}")
    public ResponseEntity<Map<String, Object>> chatAboutMedia(@PathVariable Long mediaId,
                                                              @RequestParam(required = false) String question) {
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "question_required"));
        }
        var media = mediaService.findById(mediaId);
        if (media == null) {
            return ResponseEntity.status(404).body(Map.of("error", "media_not_found"));
        }
        String answer = openAiService.chatAboutMedia(media, question);
        if (answer == null || answer.isBlank()) {
            answer = "AI chưa có câu trả lời phù hợp.";
        }
        return ResponseEntity.ok(Map.of("answer", answer));
    }
}
