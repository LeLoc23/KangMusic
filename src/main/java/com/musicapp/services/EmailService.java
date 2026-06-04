package com.musicapp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    @Value("${brevo.api-url}")
    private String brevoApiUrl;

    @Value("${brevo.api-key}")
    private String brevoApiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;

    public EmailService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Async("emailTaskExecutor")
    public void sendEmail(String to, String subject, String text) {
        try {
            Map<String, Object> payload = Map.of(
                    "sender", Map.of("email", senderEmail, "name", senderName),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "htmlContent", toHtml(text),
                    "textContent", text
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(brevoApiUrl))
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Brevo email sent to {}", to);
            } else {
                log.error("Brevo email failed for {} with status {}", to, response.statusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send Brevo email to {}: {}", to, e.getMessage());
        }
    }

    private String toHtml(String text) {
        String escaped = text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
        return "<html><body><p>" + escaped + "</p></body></html>";
    }
}
