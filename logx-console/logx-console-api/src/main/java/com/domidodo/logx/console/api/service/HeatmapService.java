package com.domidodo.logx.console.api.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.NamedValue;
import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.console.api.dto.HeatmapRequestDTO;
import com.domidodo.logx.console.api.dto.HeatmapRequestDTO.Granularity;
import com.domidodo.logx.console.api.dto.HeatmapRequestDTO.HeatType;
import com.domidodo.logx.console.api.dto.HeatmapResponseDTO;
import com.domidodo.logx.console.api.dto.HeatmapResponseDTO.*;
import com.domidodo.logx.console.api.entity.System;
import com.domidodo.logx.console.api.mapper.SystemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 热力图服务
 * 提供24小时模块调用热力图分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeatmapService {

    private final ElasticsearchClient elasticsearchClient;
    private final SystemMapper systemMapper;

    private static final String INDEX_PREFIX = "logx-logs-";

    private static final DateTimeFormatter ES_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /**
     * 热力图颜色梯度（绿->黄->红）
     */
    private static final List<String> COLOR_GRADIENT = List.of(
            "#E8F5E9", // 0-10: 极低
            "#C8E6C9", // 10-20: 很低
            "#A5D6A7", // 20-30: 低
            "#81C784", // 30-40: 较低
            "#FFF9C4", // 40-50: 中等
            "#FFE082", // 50-60: 较高
            "#FFB74D", // 60-70: 高
            "#FF8A65", // 70-80: 很高
            "#E57373", // 80-90: 极高
            "#C62828"  // 90-100: 峰值
    );

    @Value("${logx.heatmap.max-modules:50}")
    private int maxModules;

    /**
     * 获取24小时模块热力图
     *
     * @param request 热力图请求参数
     * @return 热力图数据
     */
    public HeatmapResponseDTO getModuleHeatmap(HeatmapRequestDTO request) {
        log.info("开始生成热力图: tenantId={}, systemIds={}, date={}, heatType={}",
                request.getTenantId(),
                request.getSystemIds(),
                request.getDate(),
                request.getHeatType());

        long startTime = java.lang.System.currentTimeMillis();

        try {
            // 1. 参数处理
            LocalDate queryDate = request.getDate() != null ? request.getDate() : LocalDate.now();
            int moduleLimit = Math.min(request.getModuleLimit() != null ?
                    request.getModuleLimit() : 20, maxModules);
            HeatType heatType = request.getHeatType() != null ?
                    request.getHeatType() : HeatType.CALL_COUNT;
            Granularity granularity = request.getGranularity() != null ?
                    request.getGranularity() : Granularity.HOUR;

            // 2. 构建时间范围
            LocalDateTime startDateTime = queryDate.atStartOfDay();
            LocalDateTime endDateTime = queryDate.plusDays(1).atStartOfDay();

            // 3. 获取系统信息
            Map<String, System> systemMap = getSystemMap(request.getTenantId(), request.getSystemIds());
            if (systemMap.isEmpty()) {
                log.warn("未找到符合条件的系统");
                return buildEmptyResponse(queryDate, heatType, granularity);
            }

            // 4. 构建索引模式
            String indexPattern = buildIndexPattern(request.getTenantId(), request.getSystemIds());

            // 5. 查询热力图数据
            Map<String, Map<Integer, CellData>> rawData = queryHeatmapData(
                    indexPattern, startDateTime, endDateTime, moduleLimit, granularity);

            if (rawData.isEmpty()) {
                log.info("查询结果为空");
                return buildEmptyResponse(queryDate, heatType, granularity);
            }

            // 6. 构建响应
            HeatmapResponseDTO response = buildResponse(
                    rawData, systemMap, queryDate, heatType, granularity,
                    request.getMinHeatThreshold());

            long duration = java.lang.System.currentTimeMillis() - startTime;
            log.info("热力图生成完成: 模块数={}, 耗时={}ms",
                    response.getModules().size(), duration);

            return response;

        } catch (Exception e) {
            log.error("生成热力图失败", e);
            throw new RuntimeException("生成热力图失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询热力图原始数据
     */
    private Map<String, Map<Integer, CellData>> queryHeatmapData(
            String indexPattern,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int moduleLimit,
            Granularity granularity) {

        Map<String, Map<Integer, CellData>> result = new LinkedHashMap<>();

        try {
            // 构建时间范围查询
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            boolQuery.filter(f -> f.range(r -> r
                    .field("timestamp")
                    .gte(JsonData.of(formatDateTime(startTime)))
                    .lt(JsonData.of(formatDateTime(endTime)))
            ));

            // 计算时间间隔
            String interval = granularity == Granularity.HOUR ? "1h" : "30m";

            // 构建聚合查询
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .size(0)
                    .timeout("60s")
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    // 按模块聚合
                    .aggregations("modules", a -> a
                            .terms(t -> t
                                    .field("module")
                                    .size(moduleLimit)
                                    .order(List.of(NamedValue.of("_count", SortOrder.Desc)))
                            )
                            // 子聚合：系统ID
                            .aggregations("systemId", sub -> sub
                                    .terms(st -> st.field("systemId").size(1))
                            )
                            // 子聚合：租户ID
                            .aggregations("tenantId", sub -> sub
                                    .terms(st -> st.field("tenantId").size(1))
                            )
                            // 子聚合：按时间段聚合
                            .aggregations("time_buckets", sub -> sub
                                    .dateHistogram(dh -> dh
                                            .field("timestamp")
                                            .fixedInterval(fi -> fi.time(interval))
                                            .format("HH:mm")
                                            .minDocCount(0)
                                            .extendedBounds(eb -> eb
                                                    .min(FieldDateMath.of(fd -> fd
                                                            .expr(formatDateTime(startTime))))
                                                    .max(FieldDateMath.of(fd -> fd
                                                            .expr(formatDateTime(endTime.minusMinutes(1)))))
                                            )
                                    )
                                    // 时间段内的统计
                                    .aggregations("error_count", ea -> ea
                                            .filter(f -> f.term(t -> t.field("level").value("ERROR")))
                                    )
                                    .aggregations("avg_response_time", ea -> ea
                                            .avg(avg -> avg.field("responseTime"))
                                    )
                                    .aggregations("user_count", ea -> ea
                                            .cardinality(c -> c.field("userId"))
                                    )
                            )
                            // 模块总体统计
                            .aggregations("total_errors", sub -> sub
                                    .filter(f -> f.term(t -> t.field("level").value("ERROR")))
                            )
                            .aggregations("avg_response", sub -> sub
                                    .avg(avg -> avg.field("responseTime"))
                            )
                            .aggregations("total_users", sub -> sub
                                    .cardinality(c -> c.field("userId"))
                            )
                    )
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            // 解析结果
            if (response.aggregations() != null &&
                response.aggregations().containsKey("modules")) {

                Aggregate modulesAgg = response.aggregations().get("modules");
                if (modulesAgg.isSterms()) {
                    StringTermsAggregate termsAgg = modulesAgg.sterms();

                    for (StringTermsBucket bucket : termsAgg.buckets().array()) {
                        String moduleName = bucket.key().stringValue();
                        Map<Integer, CellData> timeData = new LinkedHashMap<>();

                        // 解析时间桶
                        if (bucket.aggregations().containsKey("time_buckets")) {
                            DateHistogramAggregate dateAgg =
                                    bucket.aggregations().get("time_buckets").dateHistogram();

                            int timeIndex = 0;
                            for (DateHistogramBucket timeBucket : dateAgg.buckets().array()) {
                                CellData cellData = new CellData();
                                cellData.callCount = timeBucket.docCount();
                                cellData.timeLabel = timeBucket.keyAsString();

                                // 异常数
                                if (timeBucket.aggregations().containsKey("error_count")) {
                                    cellData.errorCount = timeBucket.aggregations()
                                            .get("error_count").filter().docCount();
                                }

                                // 平均响应时间
                                if (timeBucket.aggregations().containsKey("avg_response_time")) {
                                    Double avgTime = timeBucket.aggregations()
                                            .get("avg_response_time").avg().value();
                                    cellData.avgResponseTime = avgTime != null && !avgTime.isNaN() ?
                                            avgTime : 0.0;
                                }

                                // 用户数
                                if (timeBucket.aggregations().containsKey("user_count")) {
                                    cellData.userCount = timeBucket.aggregations()
                                            .get("user_count").cardinality().value();
                                }

                                timeData.put(timeIndex++, cellData);
                            }
                        }

                        // 存储模块的系统ID和租户ID
                        if (bucket.aggregations().containsKey("systemId")) {
                            StringTermsAggregate sysAgg =
                                    bucket.aggregations().get("systemId").sterms();
                            if (!sysAgg.buckets().array().isEmpty()) {
                                // 存储在第一个CellData中
                                if (!timeData.isEmpty()) {
                                    timeData.values().iterator().next().systemId =
                                            sysAgg.buckets().array().get(0).key().stringValue();
                                }
                            }
                        }

                        if (bucket.aggregations().containsKey("tenantId")) {
                            StringTermsAggregate tenantAgg =
                                    bucket.aggregations().get("tenantId").sterms();
                            if (!tenantAgg.buckets().array().isEmpty()) {
                                if (!timeData.isEmpty()) {
                                    timeData.values().iterator().next().tenantId =
                                            tenantAgg.buckets().array().get(0).key().stringValue();
                                }
                            }
                        }

                        // 存储模块总体统计
                        CellData firstCell = timeData.isEmpty() ? new CellData() :
                                timeData.values().iterator().next();
                        firstCell.totalCallCount = bucket.docCount();

                        if (bucket.aggregations().containsKey("total_errors")) {
                            firstCell.totalErrorCount = bucket.aggregations()
                                    .get("total_errors").filter().docCount();
                        }
                        if (bucket.aggregations().containsKey("avg_response")) {
                            Double avg = bucket.aggregations().get("avg_response").avg().value();
                            firstCell.totalAvgResponseTime = avg != null && !avg.isNaN() ? avg : 0.0;
                        }
                        if (bucket.aggregations().containsKey("total_users")) {
                            firstCell.totalUserCount = bucket.aggregations()
                                    .get("total_users").cardinality().value();
                        }

                        result.put(moduleName, timeData);
                    }
                }
            }

        } catch (Exception e) {
            log.error("查询热力图数据失败", e);
            throw new RuntimeException("查询热力图数据失败", e);
        }

        return result;
    }

    /**
     * 构建响应
     */
    private HeatmapResponseDTO buildResponse(
            Map<String, Map<Integer, CellData>> rawData,
            Map<String, System> systemMap,
            LocalDate queryDate,
            HeatType heatType,
            Granularity granularity,
            Integer minHeatThreshold) {

        // 计算时间段数量
        int timePeriods = granularity == Granularity.HOUR ? 24 : 48;

        // 生成时间标签
        List<String> timeLabels = generateTimeLabels(granularity);

        // 计算最大值（用于归一化）
        long maxValue = calculateMaxValue(rawData, heatType);

        // 构建模块信息和热力图数据
        List<ModuleInfo> modules = new ArrayList<>();
        List<List<HeatmapCell>> heatmapData = new ArrayList<>();

        // 统计汇总数据
        long totalCallCount = 0;
        long totalErrorCount = 0;
        double totalResponseTime = 0;
        int responseTimeCount = 0;
        Map<Integer, Long> hourlyCallCounts = new HashMap<>();
        Map<Integer, Long> hourlyErrorCounts = new HashMap<>();

        for (Map.Entry<String, Map<Integer, CellData>> entry : rawData.entrySet()) {
            String moduleName = entry.getKey();
            Map<Integer, CellData> timeData = entry.getValue();

            // 获取模块元信息
            CellData firstCell = timeData.values().stream().findFirst().orElse(new CellData());

            // 计算峰值
            int peakHour = 0;
            long peakValue = 0;
            for (Map.Entry<Integer, CellData> timeEntry : timeData.entrySet()) {
                long value = getValueByHeatType(timeEntry.getValue(), heatType);
                if (value > peakValue) {
                    peakValue = value;
                    peakHour = timeEntry.getKey();
                }
            }

            // 构建模块信息
            String systemId = firstCell.systemId;
            System system = systemId != null ? systemMap.get(systemId) : null;

            ModuleInfo moduleInfo = ModuleInfo.builder()
                    .moduleName(moduleName)
                    .systemId(systemId)
                    .systemName(system != null ? system.getSystemName() : systemId)
                    .tenantId(firstCell.tenantId)
                    .totalCount(firstCell.totalCallCount)
                    .errorCount(firstCell.totalErrorCount)
                    .avgResponseTime(firstCell.totalAvgResponseTime)
                    .userCount(firstCell.totalUserCount)
                    .peakHour(peakHour)
                    .peakValue(peakValue)
                    .build();

            // 累计统计
            totalCallCount += firstCell.totalCallCount != null ? firstCell.totalCallCount : 0;
            totalErrorCount += firstCell.totalErrorCount != null ? firstCell.totalErrorCount : 0;
            if (firstCell.totalAvgResponseTime != null && firstCell.totalAvgResponseTime > 0) {
                totalResponseTime += firstCell.totalAvgResponseTime;
                responseTimeCount++;
            }

            // 构建热力图行数据
            List<HeatmapCell> row = new ArrayList<>();
            for (int i = 0; i < timePeriods; i++) {
                CellData cellData = timeData.getOrDefault(i, new CellData());
                long value = getValueByHeatType(cellData, heatType);
                int heat = calculateHeat(value, maxValue);

                // 累计每小时统计
                int hour = granularity == Granularity.HOUR ? i : i / 2;
                hourlyCallCounts.merge(hour, cellData.callCount != null ? cellData.callCount : 0L, Long::sum);
                hourlyErrorCounts.merge(hour, cellData.errorCount != null ? cellData.errorCount : 0L, Long::sum);

                // 应用最小热度阈值过滤
                if (minHeatThreshold != null && heat < minHeatThreshold) {
                    heat = 0;
                }

                HeatmapCell cell = HeatmapCell.builder()
                        .timeIndex(i)
                        .timeLabel(timeLabels.get(i))
                        .value(value)
                        .heat(heat)
                        .color(getColorByHeat(heat))
                        .callCount(cellData.callCount)
                        .errorCount(cellData.errorCount)
                        .avgResponseTime(cellData.avgResponseTime)
                        .userCount(cellData.userCount)
                        .errorRate(cellData.callCount != null && cellData.callCount > 0 ?
                                Math.round((double) cellData.errorCount / cellData.callCount * 10000) / 100.0 : 0.0)
                        .build();

                row.add(cell);
            }

            modules.add(moduleInfo);
            heatmapData.add(row);
        }

        // 构建统计摘要
        HeatmapSummary summary = buildSummary(modules, hourlyCallCounts, hourlyErrorCounts,
                totalCallCount, totalErrorCount, totalResponseTime, responseTimeCount);

        // 构建颜色配置
        ColorConfig colorConfig = buildColorConfig();

        return HeatmapResponseDTO.builder()
                .date(queryDate)
                .heatType(heatType.name())
                .granularity(granularity.name())
                .timeLabels(timeLabels)
                .modules(modules)
                .heatmapData(heatmapData)
                .summary(summary)
                .colorConfig(colorConfig)
                .build();
    }

    /**
     * 构建统计摘要
     */
    private HeatmapSummary buildSummary(
            List<ModuleInfo> modules,
            Map<Integer, Long> hourlyCallCounts,
            Map<Integer, Long> hourlyErrorCounts,
            long totalCallCount,
            long totalErrorCount,
            double totalResponseTime,
            int responseTimeCount) {

        // 找峰值和低谷
        int peakHour = 0;
        long peakCallCount = 0;
        int valleyHour = 0;
        long valleyCallCount = Long.MAX_VALUE;

        for (Map.Entry<Integer, Long> entry : hourlyCallCounts.entrySet()) {
            if (entry.getValue() > peakCallCount) {
                peakCallCount = entry.getValue();
                peakHour = entry.getKey();
            }
            if (entry.getValue() < valleyCallCount && entry.getValue() > 0) {
                valleyCallCount = entry.getValue();
                valleyHour = entry.getKey();
            }
        }

        if (valleyCallCount == Long.MAX_VALUE) {
            valleyCallCount = 0;
        }

        // 找最活跃模块
        String mostActiveModule = modules.stream()
                .max(Comparator.comparingLong(m -> m.getTotalCount() != null ? m.getTotalCount() : 0L))
                .map(ModuleInfo::getModuleName)
                .orElse(null);

        // 找最高异常率模块
        String highestErrorRateModule = modules.stream()
                .filter(m -> m.getTotalCount() != null && m.getTotalCount() > 0)
                .max(Comparator.comparingDouble(m ->
                        (double) (m.getErrorCount() != null ? m.getErrorCount() : 0) / m.getTotalCount()))
                .map(ModuleInfo::getModuleName)
                .orElse(null);

        return HeatmapSummary.builder()
                .totalModules(modules.size())
                .totalCallCount(totalCallCount)
                .totalErrorCount(totalErrorCount)
                .overallErrorRate(totalCallCount > 0 ?
                        Math.round((double) totalErrorCount / totalCallCount * 10000) / 100.0 : 0.0)
                .avgResponseTime(responseTimeCount > 0 ?
                        Math.round(totalResponseTime / responseTimeCount * 100) / 100.0 : 0.0)
                .peakHour(String.format("%02d:00", peakHour))
                .peakCallCount(peakCallCount)
                .valleyHour(String.format("%02d:00", valleyHour))
                .valleyCallCount(valleyCallCount)
                .mostActiveModule(mostActiveModule)
                .highestErrorRateModule(highestErrorRateModule)
                .hourlyCallCounts(hourlyCallCounts)
                .hourlyErrorCounts(hourlyErrorCounts)
                .build();
    }

    /**
     * 构建颜色配置
     */
    private ColorConfig buildColorConfig() {
        List<ThresholdLabel> thresholds = List.of(
                ThresholdLabel.builder().min(0).max(20).label("低").color(COLOR_GRADIENT.get(1)).build(),
                ThresholdLabel.builder().min(20).max(40).label("较低").color(COLOR_GRADIENT.get(3)).build(),
                ThresholdLabel.builder().min(40).max(60).label("中等").color(COLOR_GRADIENT.get(5)).build(),
                ThresholdLabel.builder().min(60).max(80).label("较高").color(COLOR_GRADIENT.get(7)).build(),
                ThresholdLabel.builder().min(80).max(100).label("高").color(COLOR_GRADIENT.get(9)).build()
        );

        return ColorConfig.builder()
                .minColor(COLOR_GRADIENT.get(0))
                .maxColor(COLOR_GRADIENT.get(9))
                .gradient(COLOR_GRADIENT)
                .noDataColor("#F5F5F5")
                .thresholds(thresholds)
                .build();
    }

    /**
     * 生成时间标签
     */
    private List<String> generateTimeLabels(Granularity granularity) {
        List<String> labels = new ArrayList<>();

        if (granularity == Granularity.HOUR) {
            for (int i = 0; i < 24; i++) {
                labels.add(String.format("%02d:00", i));
            }
        } else {
            for (int i = 0; i < 48; i++) {
                int hour = i / 2;
                int minute = (i % 2) * 30;
                labels.add(String.format("%02d:%02d", hour, minute));
            }
        }

        return labels;
    }

    /**
     * 根据热度类型获取值
     */
    private long getValueByHeatType(CellData cellData, HeatType heatType) {
        if (cellData == null) return 0;

        return switch (heatType) {
            case CALL_COUNT -> cellData.callCount != null ? cellData.callCount : 0;
            case ERROR_COUNT -> cellData.errorCount != null ? cellData.errorCount : 0;
            case RESPONSE_TIME -> cellData.avgResponseTime != null ?
                    Math.round(cellData.avgResponseTime) : 0;
            case USER_COUNT -> cellData.userCount != null ? cellData.userCount : 0;
        };
    }

    /**
     * 计算最大值
     */
    private long calculateMaxValue(Map<String, Map<Integer, CellData>> rawData, HeatType heatType) {
        long maxValue = 1; // 避免除零

        for (Map<Integer, CellData> timeData : rawData.values()) {
            for (CellData cellData : timeData.values()) {
                long value = getValueByHeatType(cellData, heatType);
                if (value > maxValue) {
                    maxValue = value;
                }
            }
        }

        return maxValue;
    }

    /**
     * 计算热度值（0-100）
     */
    private int calculateHeat(long value, long maxValue) {
        if (maxValue <= 0 || value <= 0) {
            return 0;
        }

        // 使用对数缩放，让分布更均匀
        double logValue = Math.log1p(value);
        double logMax = Math.log1p(maxValue);
        double ratio = logValue / logMax;

        return (int) Math.round(ratio * 100);
    }

    /**
     * 根据热度获取颜色
     */
    private String getColorByHeat(int heat) {
        if (heat <= 0) {
            return "#F5F5F5"; // 无数据
        }

        int index = Math.min(heat / 10, 9);
        return COLOR_GRADIENT.get(index);
    }

    /**
     * 获取系统信息映射
     */
    private Map<String, System> getSystemMap(String tenantId, List<String> systemIds) {
        Map<String, System> systemMap = new HashMap<>();

        try {
            TenantContext.setIgnoreTenant(true);
            List<System> systems;

            if (systemIds != null && !systemIds.isEmpty()) {
                // 查询指定系统
                systems = systemIds.stream()
                        .map(systemMapper::selectBySystemId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            } else if (tenantId != null && !tenantId.isEmpty()) {
                // 查询租户下所有系统
                systems = systemMapper.selectByTenantId(tenantId);
            } else {
                // 查询所有系统
                systems = systemMapper.selectAllEnabled();
            }

            for (System system : systems) {
                systemMap.put(system.getSystemId(), system);
            }

        } catch (Exception e) {
            log.error("获取系统信息失败", e);
        }

        return systemMap;
    }

    /**
     * 构建索引模式
     */
    private String buildIndexPattern(String tenantId, List<String> systemIds) {
        StringBuilder pattern = new StringBuilder(INDEX_PREFIX);

        if (tenantId != null && !tenantId.isEmpty()) {
            pattern.append(sanitize(tenantId)).append("-");
        } else {
            pattern.append("*-");
        }

        if (systemIds != null && !systemIds.isEmpty()) {
            if (systemIds.size() == 1) {
                pattern.append(sanitize(systemIds.get(0))).append("-");
            } else {
                pattern.append("*-"); // 多系统使用通配符
            }
        } else {
            pattern.append("*-");
        }

        pattern.append("*");

        return pattern.toString();
    }

    /**
     * 清理字符串
     */
    private String sanitize(String input) {
        if (input == null) return "*";
        return input.toLowerCase().replaceAll("[^a-z0-9-]", "");
    }

    /**
     * 格式化日期时间
     */
    private String formatDateTime(LocalDateTime dateTime) {
        ZonedDateTime utc = dateTime.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC);
        return utc.format(ES_DATETIME_FORMATTER);
    }

    /**
     * 构建空响应
     */
    private HeatmapResponseDTO buildEmptyResponse(LocalDate date, HeatType heatType,
                                                  Granularity granularity) {
        return HeatmapResponseDTO.builder()
                .date(date)
                .heatType(heatType.name())
                .granularity(granularity.name())
                .timeLabels(generateTimeLabels(granularity))
                .modules(new ArrayList<>())
                .heatmapData(new ArrayList<>())
                .summary(HeatmapSummary.builder()
                        .totalModules(0)
                        .totalCallCount(0L)
                        .totalErrorCount(0L)
                        .overallErrorRate(0.0)
                        .avgResponseTime(0.0)
                        .hourlyCallCounts(new HashMap<>())
                        .hourlyErrorCounts(new HashMap<>())
                        .build())
                .colorConfig(buildColorConfig())
                .build();
    }

    /**
     * 单元格数据（内部类）
     */
    private static class CellData {
        Long callCount = 0L;
        Long errorCount = 0L;
        Double avgResponseTime = 0.0;
        Long userCount = 0L;
        String timeLabel;
        String systemId;
        String tenantId;
        // 模块总体统计
        Long totalCallCount = 0L;
        Long totalErrorCount = 0L;
        Double totalAvgResponseTime = 0.0;
        Long totalUserCount = 0L;
    }
}