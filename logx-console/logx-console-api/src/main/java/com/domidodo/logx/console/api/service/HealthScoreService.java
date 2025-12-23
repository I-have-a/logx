package com.domidodo.logx.console.api.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.domidodo.logx.console.api.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 健康度计算服务
 * <p>
 * 健康度计算公式：
 * 健康度 = 可用性 × 40% + 性能指标 × 30% + 异常率指标 × 20% + 资源使用指标 × 10%
 * <p>
 * 其中：
 * - 可用性 = (总请求数 - 失败请求数) / 总请求数 × 100%
 * - 性能指标 = 1 - (平均实际响应时间 / 最大允许响应时间)
 * - 异常率指标 = 1 - (异常数 / 总请求数)
 * - 资源使用指标 = 默认100%（暂未接入资源监控）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthScoreService {

    private final ElasticsearchClient elasticsearchClient;

    /**
     * 索引前缀
     */
    private static final String INDEX_PREFIX = "logx-logs-";

    /**
     * 最大允许响应时间（ms）
     */
    private static final double MAX_ALLOWED_RESPONSE_TIME = 5000.0;

    /**
     * 健康等级阈值
     */
    private static final double EXCELLENT_THRESHOLD = 95.0;
    private static final double GOOD_THRESHOLD = 85.0;
    private static final double FAIR_THRESHOLD = 75.0;
    private static final double POOR_THRESHOLD = 60.0;

    /**
     * 计算系统健康度
     */
    public SystemHealthDTO calculateSystemHealth(String tenantId, String systemId, int hoursAgo) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(hoursAgo);

            // 构建索引模式
            String indexPattern = buildIndexPattern(tenantId, systemId);

            // 查询统计数据
            HealthMetrics metrics = queryHealthMetrics(indexPattern, startTime, endTime);

            // 计算各项得分
            double availabilityScore = calculateAvailability(metrics);
            double performanceScore = calculatePerformance(metrics);
            double exceptionScore = calculateExceptionScore(metrics);
            double resourceScore = 100.0; // 默认100%，未接入资源监控

            // 计算综合健康度
            double overallHealth =
                    availabilityScore * 0.4 +
                    performanceScore * 0.3 +
                    exceptionScore * 0.2 +
                    resourceScore * 0.1;

            // 构建结果
            SystemHealthDTO dto = new SystemHealthDTO();
            dto.setSystemId(systemId);
            dto.setOverallHealthScore(roundToTwoDecimal(overallHealth));
            dto.setHealthLevel(determineHealthLevel(overallHealth));
            dto.setAvailabilityScore(roundToTwoDecimal(availabilityScore));
            dto.setPerformanceScore(roundToTwoDecimal(performanceScore));
            dto.setExceptionScore(roundToTwoDecimal(exceptionScore));
            dto.setResourceScore(roundToTwoDecimal(resourceScore));
            dto.setTotalRequests(metrics.totalRequests);
            dto.setFailedRequests(metrics.failedRequests);
            dto.setExceptionLogCount(metrics.exceptionCount);
            dto.setAvgResponseTime(roundToTwoDecimal(metrics.avgResponseTime));
            dto.setP99ResponseTime(roundToTwoDecimal(metrics.p99ResponseTime));
            dto.setTimeRange(String.format("最近%d小时", hoursAgo));

            // 查询健康度趋势
            if (hoursAgo >= 24) {
                dto.setHealthTrend(calculateHealthTrend(tenantId, systemId, 7));
            }

            return dto;

        } catch (Exception e) {
            log.error("计算系统健康度失败: systemId={}", systemId, e);
            return createDefaultHealth(systemId);
        }
    }

    /**
     * 查询健康指标数据
     */
    private HealthMetrics queryHealthMetrics(String indexPattern, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .size(0)  // 只需要聚合结果
                    .query(q -> q.range(r -> r
                            .field("timestamp")
                            .gte(JsonData.of(formatDateTime(startTime)))
                            .lte(JsonData.of(formatDateTime(endTime)))
                    ))
                    .aggregations("total_count", Aggregation.of(a -> a
                            .valueCount(v -> v.field("_id"))
                    ))
                    .aggregations("error_count", Aggregation.of(a -> a
                            .filter(f -> f.term(t -> t.field("level").value("ERROR")))
                    ))
                    .aggregations("avg_response_time", Aggregation.of(a -> a
                            .avg(avg -> avg.field("responseTime"))
                    ))
                    .aggregations("percentiles_response_time", Aggregation.of(a -> a
                            .percentiles(p -> p
                                    .field("responseTime")
                                    .percents(99.0)
                            )
                    ))
                    .aggregations("failed_requests", Aggregation.of(a -> a
                            .filter(f -> f.bool(b -> b
                                    .should(sh -> sh.term(t -> t.field("level").value("ERROR")))
                                    .should(sh -> sh.range(r -> r.field("statusCode").gte(JsonData.of(500))))
                            ))
                    ))
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            HealthMetrics metrics = new HealthMetrics();
            metrics.totalRequests = (long) response.aggregations().get("total_count")
                    .valueCount().value();
            metrics.exceptionCount = response.aggregations().get("error_count")
                    .filter().docCount();
            metrics.failedRequests = response.aggregations().get("failed_requests")
                    .filter().docCount();
            metrics.avgResponseTime = response.aggregations().get("avg_response_time")
                    .avg().value();

            var percentiles = response.aggregations().get("percentiles_response_time")
                    .tdigestPercentiles().values();
            if (percentiles.keyed().containsKey("99.0")) {
                metrics.p99ResponseTime = Double.valueOf(percentiles.keyed().get("99.0"));
            }

            return metrics;

        } catch (Exception e) {
            log.error("查询健康指标失败", e);
            return new HealthMetrics();
        }
    }

    /**
     * 计算可用性得分
     */
    private double calculateAvailability(HealthMetrics metrics) {
        if (metrics.totalRequests == 0) {
            return 100.0;
        }

        long successRequests = metrics.totalRequests - metrics.failedRequests;
        double availability = (double) successRequests / metrics.totalRequests * 100.0;

        return Math.max(0, Math.min(100, availability));
    }

    /**
     * 计算性能得分
     */
    private double calculatePerformance(HealthMetrics metrics) {
        if (metrics.avgResponseTime == null || metrics.avgResponseTime <= 0) {
            return 100.0;
        }

        double performanceRatio = 1.0 - (metrics.avgResponseTime / MAX_ALLOWED_RESPONSE_TIME);
        double performanceScore = performanceRatio * 100.0;

        return Math.max(0, Math.min(100, performanceScore));
    }

    /**
     * 计算异常率得分
     */
    private double calculateExceptionScore(HealthMetrics metrics) {
        if (metrics.totalRequests == 0) {
            return 100.0;
        }

        double exceptionRate = (double) metrics.exceptionCount / metrics.totalRequests;
        double exceptionScore = (1.0 - exceptionRate) * 100.0;

        return Math.max(0, Math.min(100, exceptionScore));
    }

    /**
     * 计算健康度趋势（最近N天）
     */
    private List<HealthTrendPoint> calculateHealthTrend(String tenantId, String systemId, int days) {
        List<HealthTrendPoint> trend = new ArrayList<>();

        try {
            LocalDate today = LocalDate.now();

            for (int i = days - 1; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                LocalDateTime startTime = date.atStartOfDay();
                LocalDateTime endTime = date.plusDays(1).atStartOfDay();

                String indexPattern = buildIndexPattern(tenantId, systemId);
                HealthMetrics metrics = queryHealthMetrics(indexPattern, startTime, endTime);

                double healthScore = calculateDailyHealth(metrics);

                HealthTrendPoint point = new HealthTrendPoint();
                point.setDate(date.format(DateTimeFormatter.ofPattern("MM-dd")));
                point.setHealthScore(roundToTwoDecimal(healthScore));
                point.setLogCount(metrics.totalRequests);
                point.setExceptionCount(metrics.exceptionCount);

                trend.add(point);
            }

        } catch (Exception e) {
            log.error("计算健康度趋势失败", e);
        }

        return trend;
    }

    /**
     * 计算单日健康度
     */
    private double calculateDailyHealth(HealthMetrics metrics) {
        double availabilityScore = calculateAvailability(metrics);
        double performanceScore = calculatePerformance(metrics);
        double exceptionScore = calculateExceptionScore(metrics);
        double resourceScore = 100.0;

        return availabilityScore * 0.4 +
               performanceScore * 0.3 +
               exceptionScore * 0.2 +
               resourceScore * 0.1;
    }

    /**
     * 确定健康等级
     */
    private String determineHealthLevel(double healthScore) {
        if (healthScore >= EXCELLENT_THRESHOLD) {
            return "优秀";
        } else if (healthScore >= GOOD_THRESHOLD) {
            return "良好";
        } else if (healthScore >= FAIR_THRESHOLD) {
            return "一般";
        } else if (healthScore >= POOR_THRESHOLD) {
            return "较差";
        } else {
            return "危险";
        }
    }

    /**
     * 构建索引模式
     */
    private String buildIndexPattern(String tenantId, String systemId) {
        return String.format("%s%s-%s-*",
                INDEX_PREFIX,
                sanitize(tenantId),
                sanitize(systemId));
    }

    /**
     * 清理字符串（用于索引名）
     */
    private String sanitize(String input) {
        if (input == null) return "*";
        return input.toLowerCase().replaceAll("[^a-z0-9-]", "");
    }

    /**
     * 格式化日期时间
     */
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneOffset.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
    }

    /**
     * 保留两位小数
     */
    private double roundToTwoDecimal(Double value) {
        if (value == null) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 创建默认健康度
     */
    private SystemHealthDTO createDefaultHealth(String systemId) {
        SystemHealthDTO dto = new SystemHealthDTO();
        dto.setSystemId(systemId);
        dto.setOverallHealthScore(0.0);
        dto.setHealthLevel("未知");
        dto.setAvailabilityScore(0.0);
        dto.setPerformanceScore(0.0);
        dto.setExceptionScore(0.0);
        dto.setResourceScore(0.0);
        dto.setTotalRequests(0L);
        dto.setFailedRequests(0L);
        dto.setExceptionLogCount(0L);
        dto.setAvgResponseTime(0.0);
        dto.setP99ResponseTime(0.0);
        return dto;
    }

    /**
     * 健康指标数据结构
     */
    private static class HealthMetrics {
        long totalRequests = 0;
        long failedRequests = 0;
        long exceptionCount = 0;
        Double avgResponseTime = 0.0;
        Double p99ResponseTime = 0.0;
    }
}