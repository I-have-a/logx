package com.domidodo.logx.engine.storage.lifecycle;

import com.domidodo.logx.engine.storage.config.StorageConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 热冷数据迁移策略
 * 定义数据在不同存储层之间的迁移规则
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotColdStrategy {

    private final StorageConfig storageConfig;

    /**
     * 数据层级
     */
    public enum DataTier {
        HOT,    // 热数据 - Elasticsearch，可读写
        WARM,   // 温数据 - Elasticsearch，只读
        COLD,   // 冷数据 - MinIO，归档
        DELETED // 已删除
    }

    /**
     * 判断数据应该处于哪个层级
     *
     * @param date 数据日期
     * @return 数据层级
     */
    public DataTier determineDataTier(LocalDate date) {
        LocalDate now = LocalDate.now();
        long daysDiff = now.toEpochDay() - date.toEpochDay();

        if (daysDiff <= storageConfig.getLifecycle().getHotDataDays()) {
            return DataTier.HOT;
        } else if (daysDiff <= storageConfig.getLifecycle().getWarmDataDays()) {
            return DataTier.WARM;
        } else if (daysDiff <= storageConfig.getLifecycle().getColdDataDays()) {
            return DataTier.COLD;
        } else {
            return DataTier.DELETED;
        }
    }

    /**
     * 判断是否需要迁移
     *
     * @param currentTier 当前层级
     * @param targetTier 目标层级
     * @return 是否需要迁移
     */
    public boolean needsMigration(DataTier currentTier, DataTier targetTier) {
        if (currentTier == targetTier) {
            return false;
        }

        // 只允许向下迁移（HOT -> WARM -> COLD -> DELETED）
        return currentTier.ordinal() < targetTier.ordinal();
    }

    /**
     * 获取迁移计划
     *
     * @param date 数据日期
     * @param currentTier 当前层级
     * @return 迁移计划
     */
    public MigrationPlan getMigrationPlan(LocalDate date, DataTier currentTier) {
        DataTier targetTier = determineDataTier(date);

        if (!needsMigration(currentTier, targetTier)) {
            return MigrationPlan.noAction();
        }

        return MigrationPlan.migrate(currentTier, targetTier, calculatePriority(date));
    }

    /**
     * 计算迁移优先级
     * 越早的数据优先级越高
     *
     * @param date 数据日期
     * @return 优先级（1-10，10最高）
     */
    private int calculatePriority(LocalDate date) {
        LocalDate now = LocalDate.now();
        long daysDiff = now.toEpochDay() - date.toEpochDay();

        if (daysDiff > storageConfig.getLifecycle().getColdDataDays() + 7) {
            return 10; // 超期1周，最高优先级
        } else if (daysDiff > storageConfig.getLifecycle().getColdDataDays()) {
            return 8; // 超期但不到1周
        } else if (daysDiff > storageConfig.getLifecycle().getWarmDataDays() + 3) {
            return 6; // 即将进入冷数据
        } else if (daysDiff > storageConfig.getLifecycle().getWarmDataDays()) {
            return 4; // 刚进入温数据
        } else if (daysDiff > storageConfig.getLifecycle().getHotDataDays() + 1) {
            return 2; // 即将进入温数据
        } else {
            return 1; // 热数据，低优先级
        }
    }

    /**
     * 获取存储策略配置
     */
    public Map<String, Object> getStrategyConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("hotDataDays", storageConfig.getLifecycle().getHotDataDays());
        config.put("warmDataDays", storageConfig.getLifecycle().getWarmDataDays());
        config.put("coldDataDays", storageConfig.getLifecycle().getColdDataDays());
        config.put("archiveEnabled", storageConfig.getLifecycle().getArchiveEnabled());
        config.put("cleanupEnabled", storageConfig.getLifecycle().getCleanupEnabled());
        return config;
    }

    /**
     * 获取数据层级描述
     */
    public String getDataTierDescription(DataTier tier) {
        return switch (tier) {
            case HOT -> String.format("热数据（最近%d天）- 可读写，高性能",
                    storageConfig.getLifecycle().getHotDataDays());
            case WARM -> String.format("温数据（%d-%d天）- 只读，标准性能",
                    storageConfig.getLifecycle().getHotDataDays(),
                    storageConfig.getLifecycle().getWarmDataDays());
            case COLD -> String.format("冷数据（%d-%d天）- 归档，低成本",
                    storageConfig.getLifecycle().getWarmDataDays(),
                    storageConfig.getLifecycle().getColdDataDays());
            case DELETED -> String.format("已删除（超过%d天）- 数据已清理",
                    storageConfig.getLifecycle().getColdDataDays());
        };
    }

    /**
     * 迁移计划
     */
    public static class MigrationPlan {
        private final boolean needsMigration;
        @Getter
        private final DataTier fromTier;
        @Getter
        private final DataTier toTier;
        @Getter
        private final int priority;

        private MigrationPlan(boolean needsMigration, DataTier fromTier, DataTier toTier, int priority) {
            this.needsMigration = needsMigration;
            this.fromTier = fromTier;
            this.toTier = toTier;
            this.priority = priority;
        }

        public static MigrationPlan noAction() {
            return new MigrationPlan(false, null, null, 0);
        }

        public static MigrationPlan migrate(DataTier fromTier, DataTier toTier, int priority) {
            return new MigrationPlan(true, fromTier, toTier, priority);
        }

        public boolean needsMigration() {
            return needsMigration;
        }

        @Override
        public String toString() {
            if (!needsMigration) {
                return "无需迁移";
            }
            return String.format("迁移: %s -> %s, 优先级: %d", fromTier, toTier, priority);
        }
    }

    /**
     * 获取存储成本估算
     *
     * @param dataSize 数据大小（字节）
     * @param tier 数据层级
     * @return 月度成本（虚拟单位）
     */
    public double estimateMonthlyCost(long dataSize, DataTier tier) {
        // 成本系数（相对值）
        double costFactor = switch (tier) {
            case HOT -> 1.0;    // 热数据，高性能，高成本
            case WARM -> 0.5;   // 温数据，标准性能，中等成本
            case COLD -> 0.1;   // 冷数据，归档，低成本
            case DELETED -> 0.0; // 已删除，无成本
        };

        // 假设每GB每月成本为1个单位
        double sizeInGB = dataSize / (1024.0 * 1024.0 * 1024.0);
        return sizeInGB * costFactor;
    }
}