package com.nicolasholanda.urlshortener.ratelimit;

import com.nicolasholanda.urlshortener.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBucketRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                "http://localhost:8080",
                1L,
                Duration.ofHours(1),
                new AppProperties.RateLimit(20L, 5L));

        rateLimiter = new TokenBucketRateLimiter(redis, properties);
    }

    @Test
    @DisplayName("allows a request while tokens remain")
    void allowsWhenTokensRemain() {
        when(redis.execute(any(RedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 19L, 0L));

        RateLimitResult result = rateLimiter.tryConsume("ip:127.0.0.1");

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingTokens()).isEqualTo(19L);
        assertThat(result.retryAfter()).isZero();
    }

    @Test
    @DisplayName("denies a request and reports when to retry once the bucket is empty")
    void deniesWhenBucketIsEmpty() {
        when(redis.execute(any(RedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(0L, 0L, 200L));

        RateLimitResult result = rateLimiter.tryConsume("ip:127.0.0.1");

        assertThat(result.allowed()).isFalse();
        assertThat(result.remainingTokens()).isZero();
        assertThat(result.retryAfter()).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("fails open when redis is unreachable")
    void failsOpenWhenRedisIsDown() {
        when(redis.execute(any(RedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        RateLimitResult result = rateLimiter.tryConsume("ip:127.0.0.1");

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingTokens()).isEqualTo(20L);
    }

    @Test
    @DisplayName("fails open when the script returns an unexpected payload")
    void failsOpenOnUnexpectedPayload() {
        when(redis.execute(any(RedisScript.class), any(List.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        assertThat(rateLimiter.tryConsume("ip:127.0.0.1").allowed()).isTrue();
    }
}
