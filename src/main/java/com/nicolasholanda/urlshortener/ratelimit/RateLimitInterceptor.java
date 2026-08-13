package com.nicolasholanda.urlshortener.ratelimit;

import com.nicolasholanda.urlshortener.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String API_KEY_HEADER = "X-Api-Key";

    private final TokenBucketRateLimiter rateLimiter;

    public RateLimitInterceptor(TokenBucketRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientKey = resolveClientKey(request);
        RateLimitResult result = rateLimiter.tryConsume(clientKey);

        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimiter.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, result.remainingTokens())));

        if (!result.allowed()) {
            long retryAfterSeconds = Math.max(1, result.retryAfter().toSeconds());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            throw new RateLimitExceededException(clientKey, result.retryAfter());
        }

        return true;
    }

    private String resolveClientKey(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey.trim();
        }

        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            String first = comma > 0 ? forwardedFor.substring(0, comma) : forwardedFor;
            return "ip:" + first.trim();
        }

        return "ip:" + request.getRemoteAddr();
    }
}
