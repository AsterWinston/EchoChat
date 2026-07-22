package me.aster.echochat.common.util;

/**
 * Twitter Snowflake 分布式ID生成器，生成全局唯一、大致按时间排序的64位ID。
 * @author AsterWinston
 */
public class SnowflakeIdGenerator {

    /** 自定义纪元毫秒值（2024-01-01 00:00:00 UTC） */
    private static final long EPOCH = 1704067200000L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    /** 每毫秒内序列号所占位数 */
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    /** 最大序列值（4095） */
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /** 机器/工作节点ID */
    private final long workerId;
    /** 数据中心ID */
    private final long datacenterId;
    /** 当前毫秒内的序列号 */
    private long sequence = 0L;
    /** 上一次生成ID的时间戳 */
    private long lastTimestamp = -1L;

    /**
     * @param workerId     工作节点ID（0..31）
     * @param datacenterId 数据中心ID（0..31）
     * @throws IllegalArgumentException 如果workerId或datacenterId超出范围
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId must be 0.." + MAX_WORKER_ID);
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId must be 0.." + MAX_DATACENTER_ID);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 生成下一个唯一ID。
     *
     * @return Snowflake 64位ID
     * @throws RuntimeException 如果系统时钟回拨
     */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards, refusing to generate id");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 忙等待直到下一个毫秒，以避免ID冲突。
     *
     * @param lastTimestamp 上一次生成ID的时间戳
     * @return 下一个毫秒级时间戳
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}