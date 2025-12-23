package com.domidodo.logx.engine.detection.rules;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 规则状态管理器
 * 用于维护连续请求、批量操作等需要状态的规则
 */
@Slf4j
@Component
public class RuleStateManager {

    /**
     * 连续失败计数器
     * key = ruleId:tenantId:systemId:target（如：123:tenant1:sys1:/api/order）
     */
    final Map<String, ContinuousState> continuousStateMap = new ConcurrentHashMap<>();

    /**
     * 批量操作计数器（时间窗口内的计数）
     * key = ruleId:tenantId:systemId:userId:target
     */
    final Map<String, BatchOperationState> batchOperationMap = new ConcurrentHashMap<>();

    /**
     * 记录连续失败
     */
    public int recordContinuousFailure(String key, boolean isFailed) {
        ContinuousState state = continuousStateMap.computeIfAbsent(key, k -> new ContinuousState());

        if (isFailed) {
            state.failureCount.incrementAndGet();
            state.lastFailureTime = LocalDateTime.now();
        } else {
            // 成功则重置计数
            state.failureCount.set(0);
        }

        return state.failureCount.get();
    }

    /**
     * 记录批量操作
     * @param key 唯一标识
     * @param windowSeconds 时间窗口（秒）
     * @return 时间窗口内的操作次数
     */
    public int recordBatchOperation(String key, int windowSeconds) {
        BatchOperationState state = batchOperationMap.computeIfAbsent(key, k -> new BatchOperationState());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusSeconds(windowSeconds);

        // 清理过期的时间戳
        state.timestamps.removeIf(time -> time.isBefore(windowStart));

        // 添加当前时间戳
        state.timestamps.add(now);
        state.lastOperationTime = now;

        return state.timestamps.size();
    }

    /**
     * 获取连续失败次数
     */
    public int getContinuousFailureCount(String key) {
        ContinuousState state = continuousStateMap.get(key);
        return state != null ? state.failureCount.get() : 0;
    }

    /**
     * 获取时间窗口内的操作次数
     */
    public int getBatchOperationCount(String key, int windowSeconds) {
        BatchOperationState state = batchOperationMap.get(key);
        if (state == null) {
            return 0;
        }

        LocalDateTime windowStart = LocalDateTime.now().minusSeconds(windowSeconds);
        return (int) state.timestamps.stream()
                .filter(time -> time.isAfter(windowStart))
                .count();
    }

    /**
     * 重置连续状态
     */
    public void resetContinuousState(String key) {
        continuousStateMap.remove(key);
    }

    /**
     * 重置批量操作状态
     */
    public void resetBatchOperationState(String key) {
        batchOperationMap.remove(key);
    }

    /**
     * 清理过期状态（定时任务调用）
     */
    public void cleanupExpiredStates() {
        LocalDateTime expireTime = LocalDateTime.now().minusHours(1);

        // 清理1小时未更新的连续状态
        continuousStateMap.entrySet().removeIf(entry ->
                entry.getValue().lastFailureTime.isBefore(expireTime)
        );

        // 清理1小时未更新的批量操作状态
        batchOperationMap.entrySet().removeIf(entry ->
                entry.getValue().lastOperationTime.isBefore(expireTime)
        );

        log.debug("清理过期状态。剩余 continuous: {}, batch: {}",
                continuousStateMap.size(), batchOperationMap.size());
    }

    /**
     * 连续状态
     */
    @Data
    private static class ContinuousState {
        private AtomicInteger failureCount = new AtomicInteger(0);
        private LocalDateTime lastFailureTime = LocalDateTime.now();
    }

    /**
     * 批量操作状态
     */
    @Data
    private static class BatchOperationState {
        private java.util.List<LocalDateTime> timestamps = new java.util.concurrent.CopyOnWriteArrayList<>();
        private LocalDateTime lastOperationTime = LocalDateTime.now();
    }
}