package com.domidodo.logx.console.api.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.domidodo.logx.console.api.dto.ModuleStatsDTO;
import com.domidodo.logx.console.api.dto.StatisticsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 统计分析服务（修复版）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final ElasticsearchClient elasticsearchClient;

    private static final String INDEX_PREFIX = "logx-logs-";

    /**
     * ISO 8601 格式化器（ES标准格式）
     */
    private static final DateTimeFormatter ES_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Value("${logx.analysis.max-aggregations:100}")
    private int maxAggregations;

    /**
     * 获取今日统计
     */
    public StatisticsDTO getTodayStatistics(String tenantId, String systemId) {
        StatisticsDTO stats = new StatisticsDTO();

        try {
            // 今日时间范围
            LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            LocalDateTime todayEnd = LocalDateTime.now();

            // 昨日时间范围
            LocalDateTime yesterdayStart = todayStart.minusDays(1);
            LocalDateTime yesterdayEnd = todayEnd.minusDays(1);

            // 获取今日数据
            Map<String, Long> todayData = getStatisticsData(
                    tenantId, systemId, todayStart, todayEnd);
            stats.setTodayTotal(todayData.getOrDefault("total", 0L));
            stats.setTodayErrors(todayData.getOrDefault("errors", 0L));
            stats.setActiveUsers(todayData.getOrDefault("users", 0L));
            stats.setAvgResponseTime(todayData.getOrDefault("avgResponseTime", 0L));

            // 获取昨日数据
            Map<String, Long> yesterdayData = getStatisticsData(
                    tenantId, systemId, yesterdayStart, yesterdayEnd);
            stats.setYesterdayTotal(yesterdayData.getOrDefault("total", 0L));
            stats.setYesterdayErrors(yesterdayData.getOrDefault("errors", 0L));
            stats.setYesterdayActiveUsers(yesterdayData.getOrDefault("users", 0L));
            stats.setYesterdayAvgResponseTime(yesterdayData.getOrDefault("avgResponseTime", 0L));

            // 计算同比
            stats.setTotalGrowthRate(calculateGrowthRate(
                    stats.getTodayTotal(), stats.getYesterdayTotal()));
            stats.setErrorGrowthRate(calculateGrowthRate(
                    stats.getTodayErrors(), stats.getYesterdayErrors()));
            stats.setUserGrowthRate(calculateGrowthRate(
                    stats.getActiveUsers(), stats.getYesterdayActiveUsers()));
            stats.setResponseTimeChange(
                    stats.getAvgResponseTime() - stats.getYesterdayAvgResponseTime());

        } catch (Exception e) {
            log.error("Failed to get today statistics", e);
        }

        return stats;
    }

    /**
     * 获取模块使用统计
     */
    public List<ModuleStatsDTO> getModuleStatistics(String tenantId, String systemId,
                                                    LocalDateTime startTime, LocalDateTime endTime,
                                                    String sortBy, Integer limit) {
        List<ModuleStatsDTO> result = new ArrayList<>();

        try {
            // 限制聚合数量
            int actualLimit = (limit != null && limit > 0) ?
                    Math.min(limit, maxAggregations) : 20;

            String indexPattern = buildIndexPattern(tenantId, systemId);

            // 构建查询（使用正确的时间格式）
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            if (startTime != null && endTime != null) {
                boolQuery.filter(f -> f.range(r -> r
                        .field("timestamp")
                        .gte(JsonData.of(formatDateTime(startTime)))
                        .lte(JsonData.of(formatDateTime(endTime)))
                ));
            }

            // 按模块聚合
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .size(0)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .aggregations("modules", a -> a
                            .terms(t -> t
                                    .field("module")
                                    .size(actualLimit)
                            )
                            .aggregations("users", sub -> sub
                                    .cardinality(c -> c.field("userId"))
                            )
                            .aggregations("errors", sub -> sub
                                    .filter(f -> f.term(t -> t
                                            .field("level")
                                            .value("ERROR")
                                    ))
                            )
                            .aggregations("avgResponseTime", sub -> sub
                                    .avg(avg -> avg.field("responseTime"))
                            )
                    )
            );

            SearchResponse<Map> response = elasticsearchClient.search(
                    searchRequest, Map.class);

            // 解析结果
            if (response.aggregations() != null) {
                Aggregate modulesAgg = response.aggregations().get("modules");
                if (modulesAgg.isSterms()) {
                    StringTermsAggregate termsAgg = modulesAgg.sterms();
                    int rank = 1;

                    for (StringTermsBucket bucket : termsAgg.buckets().array()) {
                        ModuleStatsDTO stat = new ModuleStatsDTO();
                        stat.setRank(rank++);
                        stat.setModuleName(bucket.key().stringValue());
                        stat.setCallCount(bucket.docCount());

                        // 用户数
                        if (bucket.aggregations().containsKey("users")) {
                            CardinalityAggregate userAgg =
                                    bucket.aggregations().get("users").cardinality();
                            stat.setUserCount(userAgg.value());
                        }

                        // 异常数和异常率
                        if (bucket.aggregations().containsKey("errors")) {
                            FilterAggregate errorAgg =
                                    bucket.aggregations().get("errors").filter();
                            long errorCount = errorAgg.docCount();
                            double errorRate = bucket.docCount() > 0 ?
                                    (double) errorCount / bucket.docCount() * 100 : 0.0;
                            stat.setErrorRate(Math.round(errorRate * 100.0) / 100.0);
                        }

                        // 平均响应时间
                        if (bucket.aggregations().containsKey("avgResponseTime")) {
                            AvgAggregate avgAgg =
                                    bucket.aggregations().get("avgResponseTime").avg();
                            stat.setAvgResponseTime(Math.round(avgAgg.value()));
                        }

                        stat.setCategory("业务模块");
                        stat.setTrend("+12%"); // TODO: 计算实际趋势

                        result.add(stat);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to get module statistics", e);
        }

        return result;
    }

    /**
     * 获取热点功能分析
     */
    public List<Map<String, Object>> getHotspotAnalysis(String tenantId, String systemId,
                                                        LocalDateTime startTime,
                                                        LocalDateTime endTime) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            String indexPattern = buildIndexPattern(tenantId, systemId);

            // 按 operation 聚合
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            if (startTime != null && endTime != null) {
                boolQuery.filter(f -> f.range(r -> r
                        .field("timestamp")
                        .gte(JsonData.of(formatDateTime(startTime)))
                        .lte(JsonData.of(formatDateTime(endTime)))
                ));
            }

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .size(0)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .aggregations("operations", a -> a
                            .terms(t -> t
                                    .field("operation")
                                    .size(Math.min(50, maxAggregations))
                            )
                            .aggregations("modules", sub -> sub
                                    .terms(st -> st.field("module").size(1))
                            )
                            .aggregations("users", sub -> sub
                                    .cardinality(c -> c.field("userId"))
                            )
                            .aggregations("avgTime", sub -> sub
                                    .avg(avg -> avg.field("responseTime"))
                            )
                    )
            );

            SearchResponse<Map> response = elasticsearchClient.search(
                    searchRequest, Map.class);

            // 解析结果
            if (response.aggregations() != null) {
                Aggregate operationsAgg = response.aggregations().get("operations");
                if (operationsAgg.isSterms()) {
                    StringTermsAggregate termsAgg = operationsAgg.sterms();
                    int rank = 1;

                    for (StringTermsBucket bucket : termsAgg.buckets().array()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("rank", rank++);
                        item.put("operation", bucket.key().stringValue());
                        item.put("callCount", bucket.docCount());

                        // 获取模块名
                        if (bucket.aggregations().containsKey("modules")) {
                            StringTermsAggregate moduleAgg =
                                    bucket.aggregations().get("modules").sterms();
                            if (!moduleAgg.buckets().array().isEmpty()) {
                                item.put("module",
                                        moduleAgg.buckets().array().get(0).key().stringValue());
                            }
                        }

                        // 用户数
                        if (bucket.aggregations().containsKey("users")) {
                            CardinalityAggregate userAgg =
                                    bucket.aggregations().get("users").cardinality();
                            item.put("userCount", userAgg.value());
                        }

                        // 平均耗时
                        if (bucket.aggregations().containsKey("avgTime")) {
                            AvgAggregate avgAgg =
                                    bucket.aggregations().get("avgTime").avg();
                            item.put("avgTime", Math.round(avgAgg.value()));
                        }

                        // 计算热度指数（简单算法）
                        long callCount = bucket.docCount();
                        long userCount = item.containsKey("userCount") ?
                                ((Number) item.get("userCount")).longValue() : 0;
                        double hotScore = Math.min(100,
                                (callCount / 100.0) + (userCount / 10.0));
                        item.put("hotScore", Math.round(hotScore * 10.0) / 10.0);

                        result.add(item);
                    }
                }
            }

            // 按热度排序
            result.sort((a, b) -> Double.compare(
                    ((Number) b.get("hotScore")).doubleValue(),
                    ((Number) a.get("hotScore")).doubleValue()
            ));

        } catch (Exception e) {
            log.error("Failed to get hotspot analysis", e);
        }

        return result;
    }

    /**
     * 格式化日期时间为 ES 标准格式
     */
    private String formatDateTime(LocalDateTime dateTime) {
        // 转换为 UTC 时间
        ZonedDateTime utc = dateTime.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC);
        return utc.format(ES_DATETIME_FORMATTER);
    }

    /**
     * 获取统计数据
     */
    private Map<String, Long> getStatisticsData(String tenantId, String systemId,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Long> data = new HashMap<>();

        try {
            String indexPattern = buildIndexPattern(tenantId, systemId);

            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            boolQuery.filter(f -> f.range(r -> r
                    .field("timestamp")
                    .gte(JsonData.of(formatDateTime(startTime)))
                    .lte(JsonData.of(formatDateTime(endTime)))
            ));

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .size(0)
                    .timeout("30s")  // 添加超时
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .aggregations("errors", a -> a
                            .filter(f -> f.term(t -> t
                                    .field("level")
                                    .value("ERROR")
                            ))
                    )
                    .aggregations("users", a -> a
                            .cardinality(c -> c.field("userId"))
                    )
                    .aggregations("avgResponseTime", a -> a
                            .avg(avg -> avg.field("responseTime"))
                    )
            );

            SearchResponse<Map> response = elasticsearchClient.search(
                    searchRequest, Map.class);

            // 总数
            data.put("total", response.hits().total() != null ?
                    response.hits().total().value() : 0L);

            // 异常数
            if (response.aggregations().containsKey("errors")) {
                FilterAggregate errorAgg =
                        response.aggregations().get("errors").filter();
                data.put("errors", errorAgg.docCount());
            }

            // 活跃用户数
            if (response.aggregations().containsKey("users")) {
                CardinalityAggregate userAgg =
                        response.aggregations().get("users").cardinality();
                data.put("users", userAgg.value());
            }

            // 平均响应时间
            if (response.aggregations().containsKey("avgResponseTime")) {
                AvgAggregate avgAgg =
                        response.aggregations().get("avgResponseTime").avg();
                data.put("avgResponseTime", Math.round(avgAgg.value()));
            }

        } catch (Exception e) {
            log.error("Failed to get statistics data", e);
        }

        return data;
    }

    /**
     * 计算增长率
     */
    private Double calculateGrowthRate(Long current, Long previous) {
        if (previous == null || previous == 0) {
            return current != null && current > 0 ? 100.0 : 0.0;
        }
        double rate = ((double) (current - previous) / previous) * 100;
        return Math.round(rate * 100.0) / 100.0;
    }

    /**
     * 构建索引模式
     */
    private String buildIndexPattern(String tenantId, String systemId) {
        StringBuilder pattern = new StringBuilder(INDEX_PREFIX);

        if (tenantId != null && !tenantId.isEmpty()) {
            pattern.append(tenantId.toLowerCase()).append("-");
        } else {
            pattern.append("*-");
        }

        if (systemId != null && !systemId.isEmpty()) {
            pattern.append(systemId.toLowerCase()).append("-");
        } else {
            pattern.append("*-");
        }

        pattern.append("*");

        return pattern.toString();
    }
}