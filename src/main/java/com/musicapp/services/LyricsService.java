package com.musicapp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicapp.models.LyricsStatus;
import com.musicapp.models.MediaItem;
import com.musicapp.models.SongLyrics;
import com.musicapp.repositories.MediaItemRepository;
import com.musicapp.repositories.SongLyricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LyricsService {

    private static final Logger log = LoggerFactory.getLogger(LyricsService.class);
    private final MediaItemRepository mediaItemRepository;
    private final SongLyricsRepository songLyricsRepository;
    private final OpenAiTranscriptionService openAiTranscriptionService;
    private final AudioProcessingService audioProcessingService;
    private final LocalStorageService localStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LyricsService(MediaItemRepository mediaItemRepository,
                         SongLyricsRepository songLyricsRepository,
                         OpenAiTranscriptionService openAiTranscriptionService,
                         AudioProcessingService audioProcessingService,
                         LocalStorageService localStorageService) {
        this.mediaItemRepository = mediaItemRepository;
        this.songLyricsRepository = songLyricsRepository;
        this.openAiTranscriptionService = openAiTranscriptionService;
        this.audioProcessingService = audioProcessingService;
        this.localStorageService = localStorageService;
    }

    @Async
    @Transactional
    public void generateLyricsAsync(Long songId) {
        log.info("Starting async lyrics generation for songId={}", songId);
        Optional<MediaItem> mediaOpt = mediaItemRepository.findById(songId);
        if (mediaOpt.isEmpty()) {
            log.error("Song with id={} not found for lyrics generation", songId);
            return;
        }

        MediaItem song = mediaOpt.get();
        song.setLyricsStatus(LyricsStatus.PROCESSING);
        mediaItemRepository.save(song);

        List<AudioProcessingService.AudioChunk> chunks = null;
        try {
            Path audioPath = localStorageService.resolveAbsolutePath(song.getFileName());
            File audioFile = audioPath.toFile();
            if (!audioFile.exists()) {
                throw new Exception("Media file not found on disk at: " + audioFile.getAbsolutePath());
            }

            chunks = audioProcessingService.prepareAudioChunks(audioFile);
            List<Map<String, Object>> allSegments = new ArrayList<>();
            StringBuilder fullRawText = new StringBuilder();

            for (AudioProcessingService.AudioChunk chunk : chunks) {
                log.info("Transcribing chunk file={} with offset={}s", chunk.getFile().getName(), chunk.getTimeOffsetSeconds());
                String jsonResult = openAiTranscriptionService.transcribe(chunk.getFile());
                if (jsonResult == null || jsonResult.trim().isEmpty()) {
                    throw new Exception("Whisper transcription returned empty response for chunk " + chunk.getFile().getName());
                }

                JsonNode root = objectMapper.readTree(jsonResult);
                JsonNode segmentsNode = root.get("segments");
                if (segmentsNode != null && segmentsNode.isArray()) {
                    for (JsonNode segment : segmentsNode) {
                        String text = segment.path("text").asText().trim();
                        double start = segment.path("start").asDouble() + chunk.getTimeOffsetSeconds();
                        double end = segment.path("end").asDouble() + chunk.getTimeOffsetSeconds();

                        Map<String, Object> segMap = new LinkedHashMap<>();
                        segMap.put("line", text);
                        segMap.put("start", start);
                        segMap.put("end", end);
                        allSegments.add(segMap);

                        fullRawText.append(text).append("\n");
                    }
                }
            }

            String finalJson = objectMapper.writeValueAsString(allSegments);
            String rawText = fullRawText.toString().trim();

            // Save/Update SongLyrics
            Optional<SongLyrics> existingLyricsOpt = songLyricsRepository.findBySongId(songId);
            SongLyrics songLyrics;
            if (existingLyricsOpt.isPresent()) {
                songLyrics = existingLyricsOpt.get();
                songLyrics.setLyricsText(rawText);
                songLyrics.setLyricsJson(finalJson);
                songLyrics.setFormat("json");
                songLyrics.setGeneratedBy("OpenAI Whisper (whisper-1)");
                songLyrics.setUpdatedAt(LocalDateTime.now());
            } else {
                songLyrics = new SongLyrics(songId, rawText, finalJson, "json", "OpenAI Whisper (whisper-1)");
            }
            songLyricsRepository.save(songLyrics);

            // Update MediaItem status
            song.setLyricsStatus(LyricsStatus.READY);
            // Sync raw lyrics to media item lyrics field as fallback/compatibility
            song.setLyrics(rawText);
            mediaItemRepository.save(song);

            log.info("Lyrics successfully generated and saved for songId={}", songId);

        } catch (Exception e) {
            log.error("Failed to generate lyrics for songId={}: {}", songId, e.getMessage(), e);
            // Refresh/fetch to avoid detached entity updates
            Optional<MediaItem> songRefreshedOpt = mediaItemRepository.findById(songId);
            if (songRefreshedOpt.isPresent()) {
                MediaItem songRefreshed = songRefreshedOpt.get();
                songRefreshed.setLyricsStatus(LyricsStatus.FAILED);
                mediaItemRepository.save(songRefreshed);
            }
        } finally {
            if (chunks != null) {
                audioProcessingService.cleanUpChunks(chunks);
            }
        }
    }
}
