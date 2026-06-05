package com.musicapp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;

@Service
public class OpenAiTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTranscriptionService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openai.api-key}")
    private String apiKey;

    public String transcribe(File audioFile) {
        return transcribe(audioFile, null);
    }

    public String transcribe(File audioFile, String songTitle) {
        try {
            // Read bytes from file
            byte[] fileBytes = Files.readAllBytes(audioFile.toPath());
            
            // Wrap in ByteArrayResource with a custom filename so RestTemplate knows it's a file upload
            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return audioFile.getName();
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + apiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);
            body.add("model", "whisper-1");
            body.add("response_format", "verbose_json");

            // Add prompt to guide Whisper to focus on song lyrics
            String prompt = "Đây là lời bài hát.";
            if (songTitle != null && !songTitle.isBlank()) {
                prompt = "Đây là lời bài hát \"" + songTitle.trim() + "\".";
            }
            body.add("prompt", prompt);

            // Set temperature to 0 to reduce hallucination
            body.add("temperature", "0");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/audio/transcriptions",
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                log.warn("Whisper transcription failed with status: {}", response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("Error transcribing audio file: {}", e.getMessage(), e);
            return null;
        }
    }
}
