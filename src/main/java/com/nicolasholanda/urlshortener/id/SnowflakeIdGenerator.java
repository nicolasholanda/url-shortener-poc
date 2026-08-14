package com.nicolasholanda.urlshortener.id;

import com.nicolasholanda.urlshortener.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {

    public static final long EPOCH = 1_735_689_600_000L;

    private static final long NODE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long NODE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + NODE_ID_BITS;

    private final long nodeId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    @Autowired
    public SnowflakeIdGenerator(AppProperties properties) {
        this(properties.nodeId());
    }

    public SnowflakeIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId must be between 0 and " + MAX_NODE_ID);
        }
        this.nodeId = nodeId;
    }

    public synchronized long nextId() {
        long timestamp = currentTime();

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("clock moved backwards, refusing to generate id for "
                    + (lastTimestamp - timestamp) + "ms");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    public long nodeId() {
        return nodeId;
    }

    public static long timestampOf(long id) {
        return (id >> TIMESTAMP_SHIFT) + EPOCH;
    }

    public static long nodeIdOf(long id) {
        return (id >> NODE_ID_SHIFT) & MAX_NODE_ID;
    }

    protected long currentTime() {
        return System.currentTimeMillis();
    }

    private long waitForNextMillis(long lastTimestamp) {
        long timestamp = currentTime();
        while (timestamp <= lastTimestamp) {
            Thread.onSpinWait();
            timestamp = currentTime();
        }
        return timestamp;
    }
}
