package com.domidodo.logx.common.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 雪花算法ID生成器
 * Twitter的SnowFlake算法，生成唯一的64位长整型ID
 */
@Slf4j
public class SnowflakeIdGenerator {

    /**
     * 起始时间戳 (2024-01-01 00:00:00)
     */
    private static final long START_TIMESTAMP = 1704038400000L;

    /**
     * 机器ID所占位数
     */
    private static final long WORKER_ID_BITS = 5L;

    /**
     * 数据中心ID所占位数
     */
    private static final long DATACENTER_ID_BITS = 5L;

    /**
     * 序列号所占位数
     */
    private static final long SEQUENCE_BITS = 12L;

    /**
     * 机器ID最大值
     */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /**
     * 数据中心ID最大值
     */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /**
     * 序列号最大值
     */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /**
     * 机器ID左移位数
     */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /**
     * 数据中心ID左移位数
     */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /**
     * 时间戳左移位数
     */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /**
     * 机器ID
     */
    private final long workerId;

    /**
     * 数据中心ID
     */
    private final long datacenterId;

    /**
     * 序列号
     */
    private long sequence = 0L;

    /**
     * 上次生成ID的时间戳
     */
    private long lastTimestamp = -1L;

    /**
     * 单例实例
     */
    private static volatile SnowflakeIdGenerator instance;

    /**
     * 构造函数
     *
     * @param workerId     机器ID (0-31)
     * @param datacenterId 数据中心ID (0-31)
     */
    private SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("Worker ID can't be greater than %d or less than 0", MAX_WORKER_ID));
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("Datacenter ID can't be greater than %d or less than 0", MAX_DATACENTER_ID));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        log.info("SnowflakeIdGenerator initialized with workerId: {}, datacenterId: {}", workerId, datacenterId);
    }

    /**
     * 获取单例实例
     */
    public static SnowflakeIdGenerator getInstance() {
        if (instance == null) {
            synchronized (SnowflakeIdGenerator.class) {
                if (instance == null) {
                    // 默认使用workerId=1, datacenterId=1
                    instance = new SnowflakeIdGenerator(1, 1);
                }
            }
        }
        return instance;
    }

    /**
     * 初始化实例（可指定workerId和datacenterId）
     */
    public static void init(long workerId, long datacenterId) {
        if (instance == null) {
            synchronized (SnowflakeIdGenerator.class) {
                if (instance == null) {
                    instance = new SnowflakeIdGenerator(workerId, datacenterId);
                }
            }
        }
    }

    /**
     * 生成下一个ID（线程安全）
     */
    public synchronized long nextId() {
        long timestamp = timeGen();

        // 时钟回拨检测
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                // 时间偏差较小，等待
                try {
                    wait(offset << 1);
                    timestamp = timeGen();
                    if (timestamp < lastTimestamp) {
                        throw new RuntimeException(
                                String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", offset));
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException(
                        String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", offset));
            }
        }

        // 同一毫秒内
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            // 序列号溢出
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 组装ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_LEFT_SHIFT)
               | (datacenterId << DATACENTER_ID_SHIFT)
               | (workerId << WORKER_ID_SHIFT)
               | sequence;
    }

    /**
     * 阻塞到下一个毫秒
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳
     */
    private long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * 生成字符串ID
     */
    public String nextIdStr() {
        return String.valueOf(nextId());
    }
}