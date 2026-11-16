package com.xiangqi.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易令牌桶限流：按来源 IP 限流，防止脚本刷接口导致内存/CPU 被打满。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String LIMIT_BODY =
            "{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后再试\"}";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final double refillPerSecond;

    public RateLimitInterceptor(
            @Value("${app.rate-limit.capacity:120}") int capacity,
            @Value("${app.rate-limit.refill-per-second:2}") double refillPerSecond) {
        this.capacity = Math.max(1, capacity);
        this.refillPerSecond = Math.max(0.1, refillPerSecond);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String clientKey = clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(clientKey, key -> new Bucket(capacity));
        if (bucket.tryConsume(refillPerSecond)) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", "1");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(LIMIT_BODY);
        return false;
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Bucket {

        private double tokens;
        private long lastRefillNanos = System.nanoTime();

        private Bucket(int capacity) {
            this.tokens = capacity;
        }

        private synchronized boolean tryConsume(double refillPerSecond) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(tokens + elapsedSeconds * refillPerSecond, 10_000);
            lastRefillNanos = now;
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }
}
