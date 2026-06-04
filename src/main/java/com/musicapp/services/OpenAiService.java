package com.musicapp.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicapp.models.MediaItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OpenAiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    @Value("${openai.api-url}")
    private String apiUrl;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.chat.enabled:true}")
    private boolean chatEnabled;

    @Value("${openai.recommendations.enabled:true}")
    private boolean recommendationsEnabled;

    public OpenAiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String chatAboutMedia(MediaItem media, String question) {
        if (!chatEnabled) {
            return "AI chat is currently disabled.";
        }

        String prompt = """
                You are KangMusic's in-app music assistant. Answer in Vietnamese.
                Use only the media context provided by the application. If information is missing, say so clearly.
                Keep the answer concise and helpful.

                Media context:
                Title: %s
                Artist/Creators: %s
                Album: %s
                Genre: %s
                Emotion: %s
                Type: %s
                Play count: %d
                Lyrics:
                %s
                """.formatted(
                media.getTitle(),
                media.getCreatorDisplayName(),
                valueOrEmpty(media.getAlbum()),
                valueOrEmpty(media.getGenre()),
                valueOrEmpty(media.getEmotionLabel()),
                media.getType() != null ? media.getType().name() : "AUDIO",
                media.getPlayCount(),
                valueOrEmpty(media.getLyrics())
        );

        return createChatCompletion(List.of(
                Map.of("role", "developer", "content", prompt),
                Map.of("role", "user", "content", question)
        ), 350);
    }

    public List<MediaItem> rerankRecommendations(MediaItem context, List<MediaItem> candidates, int limit) {
        if (!recommendationsEnabled || context == null || candidates == null || candidates.size() < 2) {
            return candidates;
        }

        try {
            List<Map<String, Object>> candidateRows = candidates.stream()
                    .limit(Math.max(limit, 20))
                    .map(m -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", m.getId());
                        row.put("title", m.getTitle());
                        row.put("artist", m.getCreatorDisplayName());
                        row.put("album", m.getAlbum());
                        row.put("genre", m.getGenre());
                        row.put("emotion", m.getEmotionLabel());
                        row.put("playCount", m.getPlayCount());
                        return row;
                    })
                    .toList();

            Map<String, Object> current = new LinkedHashMap<>();
            current.put("id", context.getId());
            current.put("title", context.getTitle());
            current.put("artist", context.getCreatorDisplayName());
            current.put("album", context.getAlbum());
            current.put("genre", context.getGenre());
            current.put("emotion", context.getEmotionLabel());

            String prompt = """
                    You rerank candidate songs for a music app. Return only a JSON array of numeric ids.
                    Rank by likely relevance to the current track using genre, emotion, artist, album, and popularity.
                    Do not add ids that are not in candidates.

                    Current track:
                    %s

                    Candidates:
                    %s
                    """.formatted(
                    objectMapper.writeValueAsString(current),
                    objectMapper.writeValueAsString(candidateRows)
            );

            String content = createChatCompletion(List.of(
                    Map.of("role", "developer", "content", "Return strict JSON only."),
                    Map.of("role", "user", "content", prompt)
            ), 250);

            List<Long> ids = extractIds(content);
            if (ids.isEmpty()) return candidates;

            Map<Long, MediaItem> byId = new HashMap<>();
            for (MediaItem candidate : candidates) {
                byId.put(candidate.getId(), candidate);
            }

            List<MediaItem> ranked = new ArrayList<>();
            Set<Long> used = new HashSet<>();
            for (Long id : ids) {
                MediaItem item = byId.get(id);
                if (item != null && used.add(id)) ranked.add(item);
            }
            candidates.stream()
                    .sorted(Comparator.comparingLong(MediaItem::getPlayCount).reversed())
                    .filter(item -> used.add(item.getId()))
                    .forEach(ranked::add);

            return ranked.stream().limit(limit).toList();
        } catch (Exception e) {
            log.warn("OpenAI recommendation rerank failed: {}", e.getMessage());
            return candidates;
        }
    }

    public Map<String, Object> recommendMedia(String userQuery, List<MediaItem> candidates) {
        if (!recommendationsEnabled || candidates == null || candidates.isEmpty()) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("response", "Xin chào! Hiện tại tôi chưa quét được thư viện nhạc. Bạn có thể nghe thử các bài hát mới nhất.");
            fallback.put("ids", List.of());
            return fallback;
        }

        try {
            List<Map<String, Object>> candidateRows = candidates.stream()
                    .map(m -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", m.getId());
                        row.put("title", m.getTitle());
                        row.put("artist", m.getCreatorDisplayName());
                        row.put("genre", m.getGenre());
                        row.put("emotion", m.getEmotionLabel());
                        return row;
                    })
                    .toList();

            String developerPrompt = """
                    You are KangMusic's AI music recommendation assistant. 
                    Your job is to recommend matching songs from the provided candidate list based on the user's mood or request.
                    You MUST return ONLY a valid JSON object with the following fields:
                    1. "response" (String): A friendly, warm response in Vietnamese explaining your choices and giving advice.
                    2. "ids" (Array of numbers): The list of track IDs from the candidates that match the user's request. Only return IDs present in the candidate list. Return up to 5-7 IDs.
                    
                    Return strictly JSON only. No markdown wrappers like ```json.
                    """;

            String userPrompt = """
                    User Request: "%s"
                    
                    Candidates:
                    %s
                    """.formatted(userQuery, objectMapper.writeValueAsString(candidateRows));

            String content = createChatCompletion(List.of(
                    Map.of("role", "developer", "content", developerPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ), 500);

            // Parse response JSON
            JsonNode root = objectMapper.readTree(content.trim());
            String responseText = root.path("response").asText("Dưới đây là một số đề xuất bài hát dành cho bạn:");
            List<Long> ids = new ArrayList<>();
            JsonNode idsNode = root.path("ids");
            if (idsNode.isArray()) {
                for (JsonNode idNode : idsNode) {
                    ids.add(idNode.asLong());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("response", responseText);
            result.put("ids", ids);
            return result;

        } catch (Exception e) {
            log.warn("OpenAI recommendation search failed: {}", e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("response", "Xin chào! Gợi ý tìm kiếm nhạc AI đang gặp lỗi phân tích. Bạn có thể nhập lại hoặc thử lại sau.");
            fallback.put("ids", List.of());
            return fallback;
        }
    }

    private String createChatCompletion(List<Map<String, String>> messages, int maxTokens) {
        return createChatCompletion(messages, maxTokens, null);
    }

    private String createChatCompletion(List<Map<String, String>> messages, int maxTokens, Map<String, Object> extraBody) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", messages);
            payload.put("temperature", 0.4);
            payload.put("max_completion_tokens", maxTokens);
            if (extraBody != null) {
                payload.putAll(extraBody);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenAI request failed with status {}", response.statusCode());
                return "Hiện tại AI chưa phản hồi được. Vui lòng thử lại sau.";
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = extractMessageContent(root.path("choices").path(0).path("message"));
            return text.isBlank() ? "AI chưa có câu trả lời phù hợp." : text;
        } catch (Exception e) {
            log.warn("OpenAI request error: {}", e.getMessage());
            return "Hiện tại AI chưa phản hồi được. Vui lòng thử lại sau.";
        }
    }

    private String extractMessageContent(JsonNode message) {
        JsonNode content = message.path("content");
        if (content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if (part.has("text")) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(part.path("text").asText(""));
                }
            }
            return sb.toString();
        }
        return content.asText("");
    }

    private List<Long> extractIds(String content) {
        try {
            String json = content.trim();
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
