package com.domidodo.logx.engine.detection.alerts;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警静默期管理器
 * 防止同一规则在短时间内重复触发告警
 * <p>
 * 支持三种静默粒度：
 * 1. RULE - 规则级别静默（同一规则不重复告警）
 * 2. TARGET - 规则+目标级别静默（同一规则的同一监控目标不重复告警）
 * 3. USER - 规则+用户级别静默（同一规则的同一用户不重复告警）
 */
@Slf4j
@Component
public class AlertSilenceManager {

    /**
     * 静默记录
     * key = 静默Key（根据粒度生成）
     * value = 静默状态
     */
    private final Map<String, SilenceState> silenceMap = new ConcurrentHashMap<>();

    /**
     * 告警聚合计数
     * 用于记录静默期内被抑制的告警次数
     */
    private final Map<String, AlertAggregation> aggregationMap = new ConcurrentHashMap<>();

    /**
     * 默认静默时长（秒）- 5分钟
     */
    private static final int DEFAULT_SILENCE_SECONDS = 300;

    /**
     * 检查是否在静默期内
     *
     * @param ruleId         规则ID
     * @param tenantId       租户ID
     * @param systemId       系统ID
     * @param silenceScope   静默粒度 (RULE/TARGET/USER)
     * @param silenceSeconds 静默时长（秒），0或负数使用默认值
     * @param targetKey      目标标识（用于 TARGET/USER 粒度）
     * @return true=在静默期内（应抑制告警），false=不在静默期（可以告警）
     */
    public boolean isInSilencePeriod(Long ruleId, String tenantId, String systemId,
                                     String silenceScope, int silenceSeconds, String targetKey) {
        String silenceKey = buildSilenceKey(ruleId, tenantId, systemId, silenceScope, targetKey);
        int effectiveSilenceSeconds = silenceSeconds > 0 ? silenceSeconds : DEFAULT_SILENCE_SECONDS;

        SilenceState state = silenceMap.get(silenceKey);
        if (state == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime silenceEndTime = state.getLastAlertTime().plusSeconds(effectiveSilenceSeconds);

        if (now.isBefore(silenceEndTime)) {
            // 在静默期内，记录被抑制的告警
            recordSuppressedAlert(silenceKey, state);

            long remainingSeconds = Duration.between(now, silenceEndTime).getSeconds();
            log.debug("告警被静默抑制: key={}, 剩余静默时间={}秒, 已抑制次数={}",
                    silenceKey, remainingSeconds, state.getSuppressedCount());
            return true;
        }

        return false;
    }

    /**
     * 记录告警触发（开始新的静默期）
     *
     * @param ruleId       规则ID
     * @param tenantId     租户ID
     * @param systemId     系统ID
     * @param silenceScope 静默粒度
     * @param targetKey    目标标识
     * @param alertLevel   告警级别
     */
    public void recordAlertTriggered(Long ruleId, String tenantId, String systemId,
                                     String silenceScope, String targetKey, String alertLevel) {
        String silenceKey = buildSilenceKey(ruleId, tenantId, systemId, silenceScope, targetKey);

        SilenceState state = new SilenceState();
        state.setRuleId(ruleId);
        state.setLastAlertTime(LocalDateTime.now());
        state.setLastAlertLevel(alertLevel);
        state.setSuppressedCount(0);

        silenceMap.put(silenceKey, state);

        // 清理该 key 的聚合记录（告警已发送）
        aggregationMap.remove(silenceKey);

        log.debug("记录告警触发，开始静默期: key={}", silenceKey);
    }

    /**
     * 检查是否应该升级告警（即使在静默期内）
     * 当新告警级别高于上次时，允许突破静默期
     *
     * @param ruleId       规则ID
     * @param tenantId     租户ID
     * @param systemId     系统ID
     * @param silenceScope 静默粒度
     * @param targetKey    目标标识
     * @param newLevel     新的告警级别
     * @return true=应该升级告警（突破静默）
     */
    public boolean shouldEscalate(Long ruleId, String tenantId, String systemId,
                                  String silenceScope, String targetKey, String newLevel) {
        String silenceKey = buildSilenceKey(ruleId, tenantId, systemId, silenceScope, targetKey);
        SilenceState state = silenceMap.get(silenceKey);

        if (state == null) {
            return false;
        }

        // 比较告警级别
        int lastPriority = getAlertPriority(state.getLastAlertLevel());
        int newPriority = getAlertPriority(newLevel);

        // 新级别优先级更高（数值更小）则升级
        return newPriority < lastPriority;
    }

    /**
     * 获取静默期内的聚合信息
     *
     * @param ruleId       规则ID
     * @param tenantId     租户ID
     * @param systemId     系统ID
     * @param silenceScope 静默粒度
     * @param targetKey    目标标识
     * @return 聚合信息，null表示没有被抑制的告警
     */
    public AlertAggregation getAggregation(Long ruleId, String tenantId, String systemId,
                                           String silenceScope, String targetKey) {
        String silenceKey = buildSilenceKey(ruleId, tenantId, systemId, silenceScope, targetKey);
        return aggregationMap.get(silenceKey);
    }

    /**
     * 手动重置静默期（用于紧急情况或管理操作）
     */
    public void resetSilence(Long ruleId, String tenantId, String systemId,
                             String silenceScope, String targetKey) {
        String silenceKey = buildSilenceKey(ruleId, tenantId, systemId, silenceScope, targetKey);
        silenceMap.remove(silenceKey);
        aggregationMap.remove(silenceKey);
        log.info("已重置静默期: key={}", silenceKey);
    }

    /**
     * 重置规则的所有静默期
     */
    public void resetRuleSilence(Long ruleId) {
        String prefix = ruleId + ":";
        silenceMap.keySet().removeIf(key -> key.startsWith(prefix));
        aggregationMap.keySet().removeIf(key -> key.startsWith(prefix));
        log.info("已重置规则的所有静默期: ruleId={}", ruleId);
    }

    /**
     * 清理过期的静默记录（定时任务）
     */
    @Scheduled(fixedRate = 600000) // 每10分钟清理一次
    public void cleanupExpiredSilences() {
        LocalDateTime expireTime = LocalDateTime.now().minusHours(2); // 2小时前的记录

        int removedCount = 0;
        var iterator = silenceMap.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().getLastAlertTime().isBefore(expireTime)) {
                iterator.remove();
                aggregationMap.remove(entry.getKey());
                removedCount++;
            }
        }

        if (removedCount > 0) {
            log.info("已清理 {} 条过期静默记录", removedCount);
        }
    }

    /**
     * 获取当前静默状态统计
     */
    public Map<String, Object> getStatistics() {
        int totalSilences = silenceMap.size();
        int totalAggregations = aggregationMap.size();
        int totalSuppressed = aggregationMap.values().stream()
                .mapToInt(AlertAggregation::getCount)
                .sum();

        return Map.of(
                "activeSilences", totalSilences,
                "pendingAggregations", totalAggregations,
                "totalSuppressedAlerts", totalSuppressed
        );
    }

    // ==================== 私有方法 ====================

    /**
     * 构建静默Key
     */
    private String buildSilenceKey(Long ruleId, String tenantId, String systemId,
                                   String silenceScope, String targetKey) {
        StringBuilder key = new StringBuilder();
        key.append(ruleId).append(":");
        key.append(tenantId).append(":");
        key.append(systemId);

        if (silenceScope == null) {
            silenceScope = "RULE";
        }

        switch (silenceScope.toUpperCase()) {
            case "TARGET", "USER" -> {
                if (targetKey != null && !targetKey.isEmpty()) {
                    key.append(":").append(targetKey);
                }
            }
            // RULE 级别不需要额外的 key
        }

        return key.toString();
    }

    /**
     * 记录被抑制的告警
     */
    private void recordSuppressedAlert(String silenceKey, SilenceState state) {
        state.setSuppressedCount(state.getSuppressedCount() + 1);

        // 更新聚合信息
        AlertAggregation aggregation = aggregationMap.computeIfAbsent(
                silenceKey, k -> new AlertAggregation());
        aggregation.increment();
        aggregation.setLastOccurrence(LocalDateTime.now());
    }

    /**
     * 获取告警级别优先级（数值越小优先级越高）
     */
    private int getAlertPriority(String level) {
        if (level == null) return 999;
        return switch (level.toUpperCase()) {
            case "CRITICAL" -> 1;
            case "WARNING" -> 2;
            case "INFO" -> 3;
            default -> 999;
        };
    }

    // ==================== 内部类 ====================

    /**
     * 静默状态
     */
    @Data
    public static class SilenceState {
        private Long ruleId;
        private LocalDateTime lastAlertTime;
        private String lastAlertLevel;
        private int suppressedCount;
    }

    /**
     * 告警聚合信息
     */
    @Data
    public static class AlertAggregation {
        private int count = 0;
        private LocalDateTime firstOccurrence = LocalDateTime.now();
        private LocalDateTime lastOccurrence = LocalDateTime.now();

        public void increment() {
            count++;
        }

        public String getSummary() {
            return String.format("在静默期内共发生 %d 次相同告警（%s ~ %s）",
                    count, firstOccurrence, lastOccurrence);
        }
    }
}