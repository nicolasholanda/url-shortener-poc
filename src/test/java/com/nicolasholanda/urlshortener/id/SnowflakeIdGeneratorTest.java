package com.nicolasholanda.urlshortener.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("generates strictly increasing ids on a single thread")
    void generatesIncreasingIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7L);

        long previous = generator.nextId();
        for (int i = 0; i < 10_000; i++) {
            long current = generator.nextId();
            assertThat(current).isGreaterThan(previous);
            previous = current;
        }
    }

    @Test
    @DisplayName("encodes the node id into every generated value")
    void encodesNodeId() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(511L);

        long id = generator.nextId();

        assertThat(SnowflakeIdGenerator.nodeIdOf(id)).isEqualTo(511L);
    }

    @Test
    @DisplayName("encodes a timestamp close to now")
    void encodesTimestamp() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        long before = System.currentTimeMillis();
        long id = generator.nextId();
        long after = System.currentTimeMillis();

        assertThat(SnowflakeIdGenerator.timestampOf(id)).isBetween(before, after);
    }

    @Test
    @DisplayName("never repeats an id under concurrent load")
    void isCollisionFreeUnderConcurrency() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3L);
        int threads = 8;
        int perThread = 5_000;

        Set<Long> ids = Collections.synchronizedSet(new HashSet<>());
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        ids.add(generator.nextId());
                    }
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(ids).hasSize(threads * perThread);
    }

    @Test
    @DisplayName("rejects a node id outside the 10 bit range")
    void rejectsInvalidNodeId() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refuses to generate ids when the clock moves backwards")
    void detectsClockSkew() {
        long[] clock = {System.currentTimeMillis()};
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L) {
            @Override
            protected long currentTime() {
                return clock[0];
            }
        };

        generator.nextId();
        clock[0] -= 5_000;

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("clock moved backwards");
    }
}
