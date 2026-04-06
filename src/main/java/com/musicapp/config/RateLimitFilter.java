package com.musicapp.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CRIT-4 FIX: In-memory per-IP rate limiter using Bucket4j.
 *
 * Limits applied:
 *   POST /register         → 5 req / minute  (prevents account spam)
 *   POST /forgot-password  → 5 req / minute  (prevents SMTP DoS)
 *   POST /api/play/**      → 30 req / minute (prevents play-count inflation)
 *
 * Uses ConcurrentHashMap for in-memory state (single-instance only).
 * For multi-instance/prod: replace bucket store with Redis via bucket4j-redis.
 */
@Component
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Per-IP buckets for auth endpoints (5 requests/minute)
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    // Per-IP buckets for play endpoint (30 requests/minute)
    private final Map<String, Bucket> playBuckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String method = request.getMethod();
        String path   = request.getRequestURI();

        if ("POST".equalsIgnoreCase(method)) {
            String ip = getClientIp(request);

            if (path.equals("/register") || path.equals("/forgot-password")) {
                Bucket bucket = authBuckets.computeIfAbsent(ip, k -> buildBucket(5, Duration.ofMinutes(1)));
                if (!bucket.tryConsume(1)) {
                    log.warn("Rate limit exceeded for {} on {}", ip, path);
                    response.setStatus(429);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Quá nhiều yêu cầu. Vui lòng thử lại sau 1 phút.\"}");
                    return;
                }
            } else if (path.startsWith("/api/play/")) {
                Bucket bucket = playBuckets.computeIfAbsent(ip, k -> buildBucket(30, Duration.ofMinutes(1)));
                if (!bucket.tryConsume(1)) {
                    log.warn("Rate limit exceeded for {} on {}", ip, path);
                    response.setStatus(429);
                    return;
                }
            }
        }

        chain.doFilter(req, res);
    }

    private static Bucket buildBucket(int capacity, Duration duration) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(capacity, duration));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Extracts the real client IP, respecting X-Forwarded-For from reverse proxies.
     * Falls back to getRemoteAddr() if header is absent.
     */
    private static String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
