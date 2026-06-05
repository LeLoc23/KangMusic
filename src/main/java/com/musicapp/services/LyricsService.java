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
import java.util.regex.Pattern;

@Service
public class LyricsService {

    private static final Logger log = LoggerFactory.getLogger(LyricsService.class);

    // Patterns to detect YouTube spam/ad lines in Whisper transcription
    private static final List<Pattern> SPAM_PATTERNS = List.of(
            Pattern.compile("(?i).*sub(scribe)?\\s*(kênh|channel).*"),
            Pattern.compile("(?i).*hãy\\s+(đăng\\s+ký|subscribe).*"),
            Pattern.compile("(?i).*like\\s+(và|and)\\s+(subscribe|đăng\\s+ký).*"),
            Pattern.compile("(?i).*nhấn\\s+(chuông|nút|like|subscribe).*"),
            Pattern.compile("(?i).*bật\\s+chuông\\s+thông\\s+báo.*"),
            Pattern.compile("(?i).*chia\\s+sẻ\\s+(video|clip).*"),
            Pattern.compile("(?i).*cảm\\s+ơn\\s+(các\\s+)?bạn\\s+đã\\s+(xem|nghe|theo\\s+dõi).*"),
            Pattern.compile("(?i).*theo\\s+dõi\\s+kênh.*"),
            Pattern.compile("(?i).*để\\s+không\\s+bỏ\\s+lỡ.*"),
            Pattern.compile("(?i).*video\\s+(mới|tiếp|hay)\\s+(nhất|nhé).*")
    );
    private final MediaItemRepository mediaItemRepository;
    private final SongLyricsRepository songLyricsRepository;
    private final OpenAiTranscriptionService openAiTranscriptionService;
    private final AudioProcessingService audioProcessingService;
    private final LocalStorageService localStorageService;
    private final LyricsProgressTracker progressTracker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LyricsService(MediaItemRepository mediaItemRepository,
                         SongLyricsRepository songLyricsRepository,
                         OpenAiTranscriptionService openAiTranscriptionService,
                         AudioProcessingService audioProcessingService,
                         LocalStorageService localStorageService,
                         LyricsProgressTracker progressTracker) {
        this.mediaItemRepository = mediaItemRepository;
        this.songLyricsRepository = songLyricsRepository;
        this.openAiTranscriptionService = openAiTranscriptionService;
        this.audioProcessingService = audioProcessingService;
        this.localStorageService = localStorageService;
        this.progressTracker = progressTracker;
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
        progressTracker.update(songId, 5, "Đang khởi tạo...");
        song.setLyricsStatus(LyricsStatus.PROCESSING);
        mediaItemRepository.save(song);

        List<AudioProcessingService.AudioChunk> chunks = null;
        try {
            progressTracker.update(songId, 10, "Đang tải file nhạc...");
            Path audioPath = localStorageService.resolveAbsolutePath(song.getFileName());
            File audioFile = audioPath.toFile();
            if (!audioFile.exists()) {
                throw new Exception("Media file not found on disk at: " + audioFile.getAbsolutePath());
            }

            progressTracker.update(songId, 15, "Đang chuẩn bị audio...");
            chunks = audioProcessingService.prepareAudioChunks(audioFile);
            int totalChunks = chunks.size();
            progressTracker.update(songId, 25, totalChunks > 1
                    ? "Đã chia " + totalChunks + " đoạn, bắt đầu phiên âm..."
                    : "Bắt đầu phiên âm Whisper...");

            List<Map<String, Object>> allSegments = new ArrayList<>();
            StringBuilder fullRawText = new StringBuilder();

            int chunkIndex = 0;
            for (AudioProcessingService.AudioChunk chunk : chunks) {
                chunkIndex++;
                int chunkPercent = 25 + (int) Math.round(65.0 * chunkIndex / totalChunks);
                String chunkMessage = totalChunks > 1
                        ? "Đang phiên âm đoạn " + chunkIndex + "/" + totalChunks + "..."
                        : "Đang phiên âm bằng Whisper...";
                progressTracker.update(songId, chunkPercent, chunkMessage);

                log.info("Transcribing chunk file={} with offset={}s", chunk.getFile().getName(), chunk.getTimeOffsetSeconds());
                String jsonResult = openAiTranscriptionService.transcribe(chunk.getFile(), song.getTitle());
                if (jsonResult == null || jsonResult.trim().isEmpty()) {
                    throw new Exception("Whisper transcription returned empty response for chunk " + chunk.getFile().getName());
                }

                JsonNode root = objectMapper.readTree(jsonResult);
                JsonNode segmentsNode = root.get("segments");
                if (segmentsNode != null && segmentsNode.isArray()) {
                    for (JsonNode segment : segmentsNode) {
                        String text = segment.path("text").asText().trim();
                        if (text.isEmpty() || isSpamLine(text)) {
                            log.debug("Filtered out spam/empty line from transcription: '{}'", text);
                            continue;
                        }
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

            // Detect Whisper hallucination: if >50% of lines are the same text, discard all
            if (!allSegments.isEmpty()) {
                Map<String, Integer> lineCounts = new HashMap<>();
                for (Map<String, Object> seg : allSegments) {
                    String line = (String) seg.get("line");
                    lineCounts.merge(line, 1, Integer::sum);
                }
                int maxRepeat = lineCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                if (maxRepeat > allSegments.size() * 0.5) {
                    String repeatedLine = lineCounts.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey).orElse("?");
                    log.warn("Whisper hallucination detected for songId={}: line '{}' repeated {}/{} times. Discarding transcription.",
                            songId, repeatedLine, maxRepeat, allSegments.size());
                    throw new Exception("Whisper hallucination detected - repeated line: " + repeatedLine);
                }
            }

            if (allSegments.isEmpty()) {
                throw new Exception("Whisper returned no valid lyrics segments after filtering");
            }

            progressTracker.update(songId, 92, "Đang lọc và xử lý lời...");
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
            progressTracker.update(songId, 96, "Đang lưu lời bài hát...");
            songLyricsRepository.save(songLyrics);

            progressTracker.update(songId, 100, "Hoàn thành!");
            // Update MediaItem status
            song.setLyricsStatus(LyricsStatus.READY);
            // Sync raw lyrics to media item lyrics field as fallback/compatibility
            song.setLyrics(rawText);
            mediaItemRepository.save(song);

            log.info("Lyrics successfully generated and saved for songId={}", songId);

        } catch (Exception e) {
            log.error("Failed to generate lyrics for songId={}: {}", songId, e.getMessage(), e);
            progressTracker.update(songId, 100, "Lỗi: " + e.getMessage());
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
            progressTracker.clear(songId);
        }
    }

    /**
     * Check if a transcription line is YouTube spam/ad content
     * (e.g., "hãy subscribe kênh", "nhấn chuông thông báo", etc.)
     */
    private boolean isSpamLine(String text) {
        for (Pattern pattern : SPAM_PATTERNS) {
            if (pattern.matcher(text).matches()) {
                return true;
            }
        }
        return false;
    }
}
