package com.nicolasholanda.urlshortener.ratelimit;

import com.nicolasholanda.urlshortener.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class TokenBucketRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);
    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redis;
    private final AppProperties properties;
    private final RedisScript<List> script;

    public TokenBucketRateLimiter(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.properties = properties;

        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        redisScript.setResultType(List.class);
        this.script = redisScript;
    }

    public RateLimitResult tryConsume(String clientKey) {
        return tryConsume(clientKey, 1);
    }

    public RateLimitResult tryConsume(String clientKey, int permits) {
        long capacity = properties.rateLimit().capacity();
        long refillPerSecond = properties.rateLimit().refillPerSecond();

        try {
            List<?> raw = redis.execute(
                    script,
                    List.of(KEY_PREFIX + clientKey),
                    String.valueOf(capacity),
                    String.valueOf(refillPerSecond),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(permits));

            if (raw == null || raw.size() < 3) {
                return RateLimitResult.allowed(capacity);
            }

            boolean allowed = toLong(raw.get(0)) == 1L;
            long remaining = toLong(raw.get(1));
            long retryAfterMillis = toLong(raw.get(2));

            return allowed
                    ? RateLimitResult.allowed(remaining)
                    : RateLimitResult.denied(remaining, Duration.ofMillis(retryAfterMillis));
        } catch (DataAccessException e) {
            log.warn("rate limiter unavailable, failing open for client {}", clientKey, e);
            return RateLimitResult.allowed(capacity);
        }
    }

    public long capacity() {
        return properties.rateLimit().capacity();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
