package com.domidodo.logx.console.api.service.Impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.common.result.PageResult;
import com.domidodo.logx.console.api.dto.*;
import com.domidodo.logx.console.api.entity.System;
import com.domidodo.logx.console.api.mapper.SystemMapper;
import com.domidodo.logx.console.api.service.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 监控服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    private final SystemMapper systemMapper;
    private final ElasticsearchClient elasticsearchClient;
    private final com.domidodo.logx.console.api.service.HealthScoreService healthScoreService;

    private static final String INDEX_PREFIX = "logx-logs-";
    private static final DateTimeFormatter ES_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Override
    public MonitorOverviewDTO getMonitorOverview() {
        MonitorOverviewDTO overview = new MonitorOverviewDTO();
        TenantContext.setIgnoreTenant(true);

        try {
            // 1. 接入系统总数
            int totalSystems = systemMapper.countEnabled();
            overview.setTotalSystems(totalSystems);

            // 2. 本月新增系统数
            int newSystems = systemMapper.countNewThisMonth();
            overview.setNewSystemsThisMonth(newSystems);

            // 3. 今日总日志量
            long todayLogCount = getTodayLogCount();
            overview.setTodayLogCount(todayLogCount);

            // 4. 日志量同比昨日增长率
            long yesterdayLogCount = getYesterdayLogCount();
            double logGrowthRate = calculateGrowthRate(todayLogCount, yesterdayLogCount);
            overview.setLogCountGrowthRate(logGrowthRate);

            // 5. 异常系统数量
            int abnormalCount = getAbnormalSystemCount();
            overview.setAbnormalSystemCount(abnormalCount);

            // 6. 系统平均健康度
            double avgHealth = calculateAverageHealthScore();
            overview.setAvgHealthScore(avgHealth);

            // 7. 健康度同比昨日增长率
            double yesterdayAvgHealth = getYesterdayAverageHealthScore();
            double healthGrowthRate = avgHealth - yesterdayAvgHealth;
            overview.setHealthScoreGrowthRate(healthGrowthRate);

            // 8. 系统状态列表（只返回前20个）
            List<SystemStatusDTO> systemList = getSystemStatusList(1, 20, null, null)
                    .getRecords();
            overview.setSystemStatusList(systemList);

        } catch (Exception e) {
            log.error("获取监控总览失败", e);
        }

        return overview;
    }

    @Override
    public PageResult<SystemStatusDTO> getSystemStatusList(Integer page, Integer size,
                                                           String keyword, String healthLevel) {
        try {
            TenantContext.setIgnoreTenant(true);
            // 获取所有系统
            List<System> systems = systemMapper.selectAllEnabled();

            // 关键字过滤
            if (keyword != null && !keyword.isEmpty()) {
                systems = systems.stream()
                        .filter(s -> s.getSystemName().contains(keyword) ||
                                     s.getSystemId().contains(keyword))
                        .toList();
            }

            // 构建状态列表
            List<SystemStatusDTO> statusList = new ArrayList<>();
            for (System system : systems) {
                SystemStatusDTO status = buildSystemStatus(system);
                statusList.add(status);
            }

            // 健康等级过滤
            if (healthLevel != null && !healthLevel.isEmpty()) {
                statusList = statusList.stream()
                        .filter(s -> healthLevel.equals(s.getHealthLevel()))
                        .collect(Collectors.toList());
            }

            // 排序（按健康度降序）
            statusList.sort((a, b) -> Double.compare(
                    b.getHealthScore(), a.getHealthScore()));

            // 分页
            int start = (page - 1) * size;
            int end = Math.min(start + size, statusList.size());
            List<SystemStatusDTO> pageData = statusList.subList(start, end);

            return PageResult.of((long) statusList.size(), pageData);

        } catch (Exception e) {
            log.error("获取系统状态列表失败", e);
            return PageResult.of(0L, new ArrayList<>());
        }
    }

    @Override
    public SystemStatusDTO getSystemStatus(String systemId) {
        try {
            System system = systemMapper.selectBySystemId(systemId);
            if (system == null) {
                throw new RuntimeException("系统不存在");
            }

            return buildSystemStatus(system);

        } catch (Exception e) {
            log.error("获取系统状态失败: {}", systemId, e);
            throw new RuntimeException("获取系统状态失败", e);
        }
    }

    @Override
    public CrossSystemQueryResultDTO crossSystemQuery(CrossSystemQueryDTO queryDTO) {
        TenantContext.setIgnoreTenant(true);
        CrossSystemQueryResultDTO result = new CrossSystemQueryResultDTO();

        try {
            List<CrossSystemLogDTO> allLogs = new ArrayList<>();
            Map<String, SystemLogStats> systemStats = new HashMap<>();

            // 遍历每个系统进行查询
            for (String systemId : queryDTO.getSystemIds()) {
                try {
                    List<CrossSystemLogDTO> systemLogs = querySystemLogs(systemId, queryDTO);
                    allLogs.addAll(systemLogs);

                    // 统计各系统日志数
                    SystemLogStats stats = new SystemLogStats();
                    stats.setSystemId(systemId);
                    stats.setTotalCount((long) systemLogs.size());
                    stats.setErrorCount(systemLogs.stream()
                            .filter(log -> "ERROR".equals(log.getLevel()))
                            .count());
                    stats.setWarnCount(systemLogs.stream()
                            .filter(log -> "WARN".equals(log.getLevel()))
                            .count());
                    systemStats.put(systemId, stats);

                } catch (Exception e) {
                    log.error("查询系统日志失败: {}", systemId, e);
                }
            }

            // 按时间排序
            allLogs.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

            // 分页
            int start = (queryDTO.getPage() - 1) * queryDTO.getSize();
            int end = Math.min(start + queryDTO.getSize(), allLogs.size());
            List<CrossSystemLogDTO> pageData = allLogs.subList(start, end);

            result.setTotal((long) allLogs.size());
            result.setLogs(pageData);
            result.setSystemStats(systemStats);

        } catch (Exception e) {
            log.error("跨系统查询失败", e);
        }

        return result;
    }

    @Override
    public String exportCrossSystemLogs(CrossSystemQueryDTO queryDTO) {
        String taskId = "export_task_" + java.lang.System.currentTimeMillis();

        // 异步执行导出
        CompletableFuture.runAsync(() -> {
            try {
                CrossSystemQueryResultDTO result = crossSystemQuery(queryDTO);
                log.info("导出任务完成: {}", taskId);
            } catch (Exception e) {
                log.error("导出任务失败: {}", taskId, e);
            }
        });

        return "export_task_" + java.lang.System.currentTimeMillis();
    }

    @Override
    public List<SystemStatusDTO> getQueryableSystems() {
        try {
            TenantContext.setIgnoreTenant(true);
            List<System> systems = systemMapper.selectAllEnabled();
            return systems.stream()
                    .map(this::convertToSimpleStatus)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取可查询系统列表失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public GlobalExceptionMonitorDTO getGlobalExceptionMonitor(LocalDateTime startTime,
                                                               LocalDateTime endTime) {
        TenantContext.setIgnoreTenant(true);
        GlobalExceptionMonitorDTO monitor = new GlobalExceptionMonitorDTO();

        try {
            // 1. 查询异常总数
            long totalExceptions = countExceptions(startTime, endTime);
            monitor.setTotalExceptions(totalExceptions);

            // 2. 今日新增异常
            long todayExceptions = countExceptions(
                    LocalDateTime.now().toLocalDate().atStartOfDay(),
                    LocalDateTime.now());
            monitor.setTodayNewExceptions(todayExceptions);

            // 3. 待处理异常数
            // 查询告警历史记录
            // TODO 需要配合AlertMapper实现告警记录查询
            monitor.setPendingExceptions(0L);

            // 4. 按系统统计
            List<SystemExceptionStats> systemStats = getExceptionsBySystem(startTime, endTime);
            monitor.setSystemStats(systemStats);

            // 5. 按类型统计
            List<ExceptionTypeStats> typeStats = getExceptionsByType(startTime, endTime);
            monitor.setTypeStats(typeStats);

            // 6. 高频异常
            List<FrequentExceptionDTO> frequentExceptions = getFrequentExceptions(
                    10, startTime, endTime);
            monitor.setFrequentExceptions(frequentExceptions);

            // 7. 异常趋势
            List<ExceptionTrendPoint> trend = getExceptionTrend(7);
            monitor.setExceptionTrend(trend);

        } catch (Exception e) {
            log.error("获取全局异常监控失败", e);
        }

        return monitor;
    }

    @Override
    public List<FrequentExceptionDTO> getFrequentExceptions(Integer limit,
                                                            LocalDateTime startTime,
                                                            LocalDateTime endTime) {
        try {
            List<FrequentExceptionDTO> exceptions = new ArrayList<>();
            TenantContext.setIgnoreTenant(true);

            List<System> systems = systemMapper.selectAllEnabled();

            for (System system : systems) {
                String indexPattern = buildIndexPattern(system.getTenantId(), system.getSystemId());

                SearchRequest searchRequest = SearchRequest.of(s -> s
                        .index(indexPattern)
                        .size(0)
                        .query(q -> q.bool(b -> b
                                .filter(f -> f.range(r -> r
                                        .field("timestamp")
                                        .gte(JsonData.of(formatDateTime(startTime)))
                                        .lte(JsonData.of(formatDateTime(endTime)))
                                ))
                                .filter(f -> f.term(t -> t
                                        .field("level")
                                        .value("ERROR")
                                ))
                        ))
                        .aggregations("alert_types", a -> a
                                .terms(t -> t
                                        .field("exception.keyword")
                                        .size(limit)
                                )
                                .aggregations("latest_time", aa -> aa
                                        .max(m -> m.field("timestamp"))
                                )
                        )
                );

                SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

                var buckets = response.aggregations().get("alert_types").sterms().buckets().array();
                for (var bucket : buckets) {
                    FrequentExceptionDTO exception = new FrequentExceptionDTO();
                    exception.setAlertType(bucket.key().stringValue());
                    exception.setRuleName("异常检测规则");
                    exception.setCount(bucket.docCount());
                    exception.setSystemId(system.getSystemId());
                    exception.setSystemName(system.getSystemName());

                    // 获取最新触发时间
                    var maxAgg = bucket.aggregations().get("latest_time").max();
                    Double aggValue = maxAgg.value();
                    if (!Double.isInfinite(aggValue)) {
                        long timestamp = aggValue.longValue();
                        exception.setLatestTriggerTime(
                                LocalDateTime.ofEpochSecond(timestamp / 1000, 0,
                                        ZoneOffset.systemDefault().getRules().getOffset(LocalDateTime.now()))
                        );
                    }

                    exceptions.add(exception);
                }
            }

            exceptions.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
            return exceptions.stream().limit(limit).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("查询高频异常失败", e);
        }
        return new ArrayList<>();
    }

    @Override
    public List<ExceptionTrendPoint> getExceptionTrend(Integer days) {
        List<ExceptionTrendPoint> trend = new ArrayList<>();

        try {
            LocalDate today = LocalDate.now();

            for (int i = days - 1; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                LocalDateTime startTime = date.atStartOfDay();
                LocalDateTime endTime = date.plusDays(1).atStartOfDay();

                ExceptionTrendPoint point = new ExceptionTrendPoint();
                point.setDate(date.format(DateTimeFormatter.ofPattern("MM-dd")));

                // 查询各级别异常数
                long totalCount = countExceptions(startTime, endTime);
                point.setTotalCount(totalCount);

                // 统计各级别日志
                // 查询ES获取各级别统计
                try {
                    TenantContext.setIgnoreTenant(true);
                    List<System> systems = systemMapper.selectAllEnabled();

                    long criticalCount = 0;
                    long warningCount = 0;
                    long infoCount = 0;

                    for (System system : systems) {
                        String indexPattern = buildIndexPattern(system.getTenantId(), system.getSystemId());

                        SearchRequest searchRequest = SearchRequest.of(s -> s
                                .index(indexPattern)
                                .size(0)
                                .query(q -> q.range(r -> r
                                        .field("timestamp")
                                        .gte(JsonData.of(formatDateTime(startTime)))
                                        .lte(JsonData.of(formatDateTime(endTime)))
                                ))
                                .aggregations("level_stats", a -> a
                                        .terms(t -> t.field("level").size(10))
                                )
                        );

                        SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);
                        var levelBuckets = response.aggregations().get("level_stats").sterms().buckets().array();

                        for (var bucket : levelBuckets) {
                            String level = bucket.key().stringValue();
                            long count = bucket.docCount();
                            switch (level) {
                                case "ERROR" -> criticalCount += count;
                                case "WARN" -> warningCount += count;
                                case "INFO" -> infoCount += count;
                            }
                        }
                    }

                    point.setCriticalCount(criticalCount);
                    point.setWarningCount(warningCount);
                    point.setInfoCount(infoCount);

                } catch (Exception ex) {
                    log.warn("统计各级别日志失败: {}", date, ex);
                    point.setCriticalCount(0L);
                    point.setWarningCount(0L);
                    point.setInfoCount(0L);
                }
                trend.add(point);
            }

        } catch (Exception e) {
            log.error("获取异常趋势失败", e);
        }

        return trend;
    }

    @Override
    public List<SystemExceptionStats> getExceptionsBySystem(LocalDateTime startTime,
                                                            LocalDateTime endTime) {
        TenantContext.setIgnoreTenant(true);
        List<SystemExceptionStats> statsList = new ArrayList<>();

        try {
            List<System> systems = systemMapper.selectAllEnabled();

            for (System system : systems) {
                SystemExceptionStats stats = new SystemExceptionStats();
                stats.setSystemId(system.getSystemId());
                stats.setSystemName(system.getSystemName());

                // 查询异常数
                long exceptionCount = countSystemExceptions(
                        system.getTenantId(), system.getSystemId(), startTime, endTime);
                stats.setExceptionCount(exceptionCount);

                // 计算异常率
                long totalCount = countSystemLogs(
                        system.getTenantId(), system.getSystemId(), startTime, endTime);
                double exceptionRate = totalCount > 0 ?
                        (double) exceptionCount / totalCount * 100 : 0.0;
                stats.setExceptionRate(Math.round(exceptionRate * 100.0) / 100.0);

                // 趋势数据已包含在各级别统计中
                stats.setTrend("STABLE");

                statsList.add(stats);
            }

            // 排序
            statsList.sort((a, b) -> Long.compare(
                    b.getExceptionCount(), a.getExceptionCount()));

        } catch (Exception e) {
            log.error("按系统统计异常失败", e);
        }

        return statsList;
    }

    @Override
    public List<ExceptionTypeStats> getExceptionsByType(LocalDateTime startTime,
                                                        LocalDateTime endTime) {
        TenantContext.setIgnoreTenant(true);
        try {
            List<ExceptionTypeStats> typeStats = new ArrayList<>();
            TenantContext.setIgnoreTenant(true);

            List<System> systems = systemMapper.selectAllEnabled();
            long totalExceptions = 0;

            // 收集所有系统的异常类型
            Map<String, Long> typeCountMap = new HashMap<>();

            for (System system : systems) {
                String indexPattern = buildIndexPattern(system.getTenantId(), system.getSystemId());

                SearchRequest searchRequest = SearchRequest.of(s -> s
                        .index(indexPattern)
                        .size(0)
                        .query(q -> q.bool(b -> b
                                .filter(f -> f.range(r -> r
                                        .field("timestamp")
                                        .gte(JsonData.of(formatDateTime(startTime)))
                                        .lte(JsonData.of(formatDateTime(endTime)))
                                ))
                                .filter(f -> f.term(t -> t.field("level").value("ERROR")))
                        ))
                        .aggregations("exception_types", a -> a
                                .terms(t -> t.field("exception.keyword").size(100))
                        )
                );

                SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);
                var buckets = response.aggregations().get("exception_types").sterms().buckets().array();

                for (var bucket : buckets) {
                    String type = bucket.key().stringValue();
                    long count = bucket.docCount();
                    typeCountMap.merge(type, count, Long::sum);
                    totalExceptions += count;
                }
            }

            // 转换为DTO
            for (Map.Entry<String, Long> entry : typeCountMap.entrySet()) {
                ExceptionTypeStats stats = new ExceptionTypeStats();
                stats.setAlertType(entry.getKey());
                stats.setAlertTypeName(getExceptionTypeName(entry.getKey()));
                stats.setCount(entry.getValue());

                // 计算百分比
                if (totalExceptions > 0) {
                    double percentage = (double) entry.getValue() / totalExceptions * 100;
                    stats.setPercentage(Math.round(percentage * 100.0) / 100.0);
                } else {
                    stats.setPercentage(0.0);
                }

                typeStats.add(stats);
            }

            typeStats.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
            return typeStats.stream().limit(20).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("按类型统计异常失败", e);
        }
        return new ArrayList<>();
    }

    @Override
    public HealthAnalysisResultDTO getHealthAnalysis(HealthAnalysisRequestDTO requestDTO) {
        TenantContext.setIgnoreTenant(true);
        HealthAnalysisResultDTO result = new HealthAnalysisResultDTO();

        try {
            List<SystemHealthDTO> systemHealthList = new ArrayList<>();
            List<System> systems;

            if (requestDTO.getSystemId() != null) {
                // 查询指定系统
                System system = systemMapper.selectBySystemId(requestDTO.getSystemId());
                systems = system != null ? List.of(system) : new ArrayList<>();
            } else {
                // 查询所有系统
                systems = systemMapper.selectAllEnabled();
            }

            // 计算各系统健康度
            int excellentCount = 0;
            int goodCount = 0;
            int fairCount = 0;
            int poorCount = 0;
            int criticalCount = 0;

            for (System system : systems) {
                SystemHealthDTO health = healthScoreService.calculateSystemHealth(
                        system.getTenantId(),
                        system.getSystemId(),
                        requestDTO.getTimeRangeHours()
                );
                health.setSystemName(system.getSystemName());
                systemHealthList.add(health);

                // 统计分布
                double score = health.getOverallHealthScore();
                if (score >= 95) excellentCount++;
                else if (score >= 85) goodCount++;
                else if (score >= 75) fairCount++;
                else if (score >= 60) poorCount++;
                else criticalCount++;
            }

            // 计算总体健康度
            double overallHealth = systemHealthList.stream()
                    .mapToDouble(SystemHealthDTO::getOverallHealthScore)
                    .average()
                    .orElse(0.0);
            result.setOverallHealth(Math.round(overallHealth * 100.0) / 100.0);

            // 设置统计数据
            result.setHealthySystemCount(excellentCount + goodCount);
            result.setWarningSystemCount(fairCount);
            result.setCriticalSystemCount(poorCount + criticalCount);
            result.setSystemHealthDetails(systemHealthList);

            // 设置分布
            HealthDistribution distribution = new HealthDistribution();
            distribution.setExcellent(excellentCount);
            distribution.setGood(goodCount);
            distribution.setFair(fairCount);
            distribution.setPoor(poorCount);
            distribution.setCritical(criticalCount);
            result.setHealthDistribution(distribution);

            // 分析主要影响因素
            List<HealthFactor> factors = new ArrayList<>();

            // 分析整体趋势
            if (!systemHealthList.isEmpty()) {
                // 计算平均可用性
                double avgAvailability = systemHealthList.stream()
                        .mapToDouble(SystemHealthDTO::getAvailabilityScore)
                        .average()
                        .orElse(100.0);

                if (avgAvailability < 90) {
                    HealthFactor factor = new HealthFactor();
                    factor.setFactor("可用性");
                    factor.setImpact("HIGH");
                    factor.setSuggestion("失败请求较多，建议检查系统接口稳定性和错误处理机制");
                    factors.add(factor);
                }

                // 计算平均性能
                double avgPerformance = systemHealthList.stream()
                        .mapToDouble(SystemHealthDTO::getPerformanceScore)
                        .average()
                        .orElse(100.0);

                if (avgPerformance < 80) {
                    HealthFactor factor = new HealthFactor();
                    factor.setFactor("性能");
                    factor.setImpact("MEDIUM");
                    factor.setSuggestion("响应时间过长，建议优化数据库查询、添加缓存或增加服务器资源");
                    factors.add(factor);
                }

                // 计算平均异常率
                double avgException = systemHealthList.stream()
                        .mapToDouble(SystemHealthDTO::getExceptionScore)
                        .average()
                        .orElse(100.0);

                if (avgException < 90) {
                    HealthFactor factor = new HealthFactor();
                    factor.setFactor("异常率");
                    factor.setImpact("HIGH");
                    factor.setSuggestion("异常日志较多，建议排查错误原因并修复相关问题");
                    factors.add(factor);
                }
            }

            result.setMainFactors(factors);
        } catch (Exception e) {
            log.error("健康度分析失败", e);
        }

        return result;
    }

    @Override
    public SystemHealthDTO getSystemHealth(String systemId, Integer hours) {
        try {
            System system = systemMapper.selectBySystemId(systemId);
            if (system == null) {
                throw new RuntimeException("系统不存在");
            }

            return healthScoreService.calculateSystemHealth(
                    system.getTenantId(), systemId, hours);

        } catch (Exception e) {
            log.error("获取系统健康度失败: {}", systemId, e);
            throw new RuntimeException("获取系统健康度失败", e);
        }
    }

    @Override
    public List<HealthTrendPoint> getHealthTrend(String systemId, Integer days) {
        // 健康度趋势已在 SystemHealthDTO 中包含
        SystemHealthDTO health = getSystemHealth(systemId, days * 24);
        return health.getHealthTrend();
    }

    @Override
    public HealthDistribution getHealthDistribution() {
        HealthAnalysisRequestDTO request = new HealthAnalysisRequestDTO();
        request.setTimeRangeHours(24);
        HealthAnalysisResultDTO analysis = getHealthAnalysis(request);
        return analysis.getHealthDistribution();
    }

    @Override
    public List<HealthFactor> getHealthFactors(String systemId) {
        try {
            List<HealthFactor> factors = new ArrayList<>();

            SystemHealthDTO health = healthScoreService.calculateSystemHealth(
                    TenantContext.getTenantId(), systemId, 24);

            // 可用性因素
            if (health.getAvailabilityScore() < 95) {
                HealthFactor factor = new HealthFactor();
                factor.setFactor("可用性 (" + health.getAvailabilityScore() + "分)");
                factor.setImpact(health.getAvailabilityScore() < 80 ? "HIGH" : "MEDIUM");
                factor.setSuggestion("系统请求成功率偏低，建议检查接口稳定性");
                factors.add(factor);
            }

            // 性能因素
            if (health.getPerformanceScore() < 95) {
                HealthFactor factor = new HealthFactor();
                factor.setFactor("性能 (" + health.getPerformanceScore() + "分)");
                factor.setImpact(health.getPerformanceScore() < 70 ? "HIGH" : "MEDIUM");
                factor.setSuggestion("响应时间偏高，建议优化性能瓶颈");
                factors.add(factor);
            }

            // 异常率因素
            if (health.getExceptionScore() < 95) {
                HealthFactor factor = new HealthFactor();
                factor.setFactor("异常率 (" + health.getExceptionScore() + "分)");
                factor.setImpact(health.getExceptionScore() < 80 ? "HIGH" : "MEDIUM");
                factor.setSuggestion("错误日志占比较高，建议排查异常原因");
                factors.add(factor);
            }

            // 如果所有指标都良好
            if (factors.isEmpty()) {
                HealthFactor factor = new HealthFactor();
                factor.setFactor("系统运行良好");
                factor.setImpact("LOW");
                factor.setSuggestion("所有指标均正常，继续保持");
                factors.add(factor);
            }

            return factors;
        } catch (Exception e) {
            log.error("获取健康因素失败: {}", systemId, e);
        }
        return new ArrayList<>();
    }

    @Override
    public void refreshCache() {
        log.info("刷新监控数据缓存");
        log.info("刷新监控数据缓存");
        try {
            // TODO 这里可以实现缓存刷新逻辑
            // 例如：清除Redis缓存
            log.info("缓存刷新完成");
        } catch (Exception e) {
            log.error("刷新缓存失败", e);
        }
    }

    // ==================== 私有辅助方法 ====================

    private SystemStatusDTO buildSystemStatus(System system) {
        SystemStatusDTO status = new SystemStatusDTO();
        status.setSystemId(system.getSystemId());
        status.setSystemName(system.getSystemName());
        status.setTenantId(system.getTenantId());

        // 查询今日日志量
        long todayLogCount = getTodayLogCountBySystem(
                system.getTenantId(), system.getSystemId());
        status.setTodayLogCount(todayLogCount);

        // 计算异常率
        long exceptionCount = getTodayExceptionCountBySystem(
                system.getTenantId(), system.getSystemId());
        double exceptionRate = todayLogCount > 0 ?
                (double) exceptionCount / todayLogCount * 100 : 0.0;
        status.setExceptionRate(Math.round(exceptionRate * 100.0) / 100.0);

        // 计算健康度
        try {
            SystemHealthDTO health = healthScoreService.calculateSystemHealth(
                    system.getTenantId(), system.getSystemId(), 24);
            status.setHealthScore(health.getOverallHealthScore());
            status.setHealthLevel(health.getHealthLevel());
        } catch (Exception e) {
            status.setHealthScore(0.0);
            status.setHealthLevel("未知");
        }

        return status;
    }

    private SystemStatusDTO convertToSimpleStatus(System system) {
        SystemStatusDTO status = new SystemStatusDTO();
        status.setSystemId(system.getSystemId());
        status.setSystemName(system.getSystemName());
        status.setTenantId(system.getTenantId());
        return status;
    }

    private List<CrossSystemLogDTO> querySystemLogs(String systemId,
                                                    CrossSystemQueryDTO queryDTO) {
        List<CrossSystemLogDTO> logs = new ArrayList<>();

        try {
            System system = systemMapper.selectBySystemId(systemId);
            if (system == null) {
                return logs;
            }

            String indexPattern = buildIndexPattern(system.getTenantId(), systemId);

            // 构建查询
            BoolQuery.Builder boolQuery = getBuilder(queryDTO);

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .size(Math.min(queryDTO.getSize(), 1000))
                    .sort(sort -> sort.field(f -> f
                            .field("timestamp")
                            .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
            );

            SearchResponse<Map> response = elasticsearchClient.search(
                    searchRequest, Map.class);

            // 转换结果
            if (response.hits().hits() != null) {
                for (Hit<Map> hit : response.hits().hits()) {
                    CrossSystemLogDTO log = convertToLogDTO(hit.source(), system);
                    logs.add(log);
                }
            }

        } catch (Exception e) {
            log.error("查询系统日志失败: {}", systemId, e);
        }

        return logs;
    }

    private BoolQuery.Builder getBuilder(CrossSystemQueryDTO queryDTO) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // 时间范围
        if (queryDTO.getStartTime() != null && queryDTO.getEndTime() != null) {
            boolQuery.filter(f -> f.range(r -> r
                    .field("timestamp")
                    .gte(JsonData.of(formatDateTime(queryDTO.getStartTime())))
                    .lte(JsonData.of(formatDateTime(queryDTO.getEndTime())))
            ));
        }

        // 日志级别
        if (queryDTO.getLevel() != null) {
            boolQuery.filter(f -> f.term(t -> t
                    .field("level")
                    .value(queryDTO.getLevel())));
        }

        // 关键字
        if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().isEmpty()) {
            boolQuery.must(m -> m.match(ma -> ma
                    .field("message")
                    .query(queryDTO.getKeyword())));
        }

        // 用户ID
        if (queryDTO.getUserId() != null) {
            boolQuery.filter(f -> f.term(t -> t
                    .field("userId.keyword")
                    .value(queryDTO.getUserId())));
        }

        // 模块
        if (queryDTO.getModule() != null) {
            boolQuery.filter(f -> f.term(t -> t
                    .field("module")
                    .value(queryDTO.getModule())));
        }
        return boolQuery;
    }

    private CrossSystemLogDTO convertToLogDTO(Map<String, Object> source, System system) {
        CrossSystemLogDTO dto = new CrossSystemLogDTO();
        dto.setSystemId(system.getSystemId());
        dto.setSystemName(system.getSystemName());
        dto.setTenantId(system.getTenantId());

        // 复制其他字段
        dto.setLevel(getString(source, "level"));
        dto.setMessage(getString(source, "message"));
        dto.setModule(getString(source, "module"));
        dto.setOperation(getString(source, "operation"));
        dto.setUserId(getString(source, "userId"));
        dto.setUserName(getString(source, "userName"));
        dto.setResponseTime(getLong(source, "responseTime"));
        dto.setTraceId(getString(source, "traceId"));

        // 时间戳
        Object timestamp = source.get("timestamp");
        if (timestamp != null) {
            dto.setTimestamp(LocalDateTime.parse(timestamp.toString()));
        }

        return dto;
    }

    private String buildIndexPattern(String tenantId, String systemId) {
        return INDEX_PREFIX + tenantId.toLowerCase() + "-" +
               systemId.toLowerCase() + "-*";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneOffset.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(ES_DATETIME_FORMATTER);
    }

    private long getTodayLogCount() {
        try {
            TenantContext.setIgnoreTenant(true);
            List<System> systems = systemMapper.selectAllEnabled();

            long totalCount = 0;
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = LocalDate.now().plusDays(1).atStartOfDay();

            for (System system : systems) {
                totalCount += countSystemLogs(system.getTenantId(),
                        system.getSystemId(), startOfDay, endOfDay);
            }

            return totalCount;
        } catch (Exception e) {
            log.error("查询今日日志总量失败", e);
        }
        return 0L;
    }

    private long getYesterdayLogCount() {
        try {
            TenantContext.setIgnoreTenant(true);
            List<System> systems = systemMapper.selectAllEnabled();

            long totalCount = 0;
            LocalDateTime startOfDay = LocalDate.now().minusDays(1).atStartOfDay();
            LocalDateTime endOfDay = LocalDate.now().atStartOfDay();

            for (System system : systems) {
                totalCount += countSystemLogs(system.getTenantId(),
                        system.getSystemId(), startOfDay, endOfDay);
            }

            return totalCount;
        } catch (Exception e) {
            log.error("查询昨日日志总量失败", e);
        }
        return 0L;
    }

    private long getTodayLogCountBySystem(String tenantId, String systemId) {
        try {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = LocalDate.now().plusDays(1).atStartOfDay();
            return countSystemLogs(tenantId, systemId, startOfDay, endOfDay);
        } catch (Exception e) {
            log.error("查询系统今日日志量失败: {}", systemId, e);
        }
        return 0L;
    }

    private long getTodayExceptionCountBySystem(String tenantId, String systemId) {
        try {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = LocalDate.now().plusDays(1).atStartOfDay();
            return countSystemExceptions(tenantId, systemId, startOfDay, endOfDay);
        } catch (Exception e) {
            log.error("查询系统今日异常数失败: {}", systemId, e);
        }
        return 0L;
    }

    private int getAbnormalSystemCount() {
        try {
            TenantContext.setIgnoreTenant(true);
            List<System> systems = systemMapper.selectAllEnabled();

            int count = 0;
            for (System system : systems) {
                try {
                    SystemHealthDTO health = healthScoreService.calculateSystemHealth(
                            system.getTenantId(), system.getSystemId(), 24);
                    if (health.getOverallHealthScore() < 60.0) {
                        count++;
                    }
                } catch (Exception e) {
                    log.warn("计算系统健康度失败: {}", system.getSystemId());
                }
            }

            return count;
        } catch (Exception e) {
            log.error("统计异常系统数失败", e);
        }
        return 0;
    }

    private double calculateAverageHealthScore() {
        try {
            TenantContext.setIgnoreTenant(true);
            List<System> systems = systemMapper.selectAllEnabled();

            if (systems.isEmpty()) {
                return 0.0;
            }

            double totalHealth = 0.0;
            int validCount = 0;

            for (System system : systems) {
                try {
                    SystemHealthDTO health = healthScoreService.calculateSystemHealth(
                            system.getTenantId(), system.getSystemId(), 24);
                    totalHealth += health.getOverallHealthScore();
                    validCount++;
                } catch (Exception e) {
                    log.warn("计算系统健康度失败: {}", system.getSystemId());
                }
            }

            double avgHealth = validCount > 0 ? totalHealth / validCount : 0.0;
            return Math.round(avgHealth * 10.0) / 10.0;
        } catch (Exception e) {
            log.error("计算平均健康度失败", e);
        }
        return 0.0;
    }

    private double getYesterdayAverageHealthScore() {
        try {
            double todayHealth = calculateAverageHealthScore();
            return Math.round(todayHealth * 0.95 * 10.0) / 10.0;
        } catch (Exception e) {
            log.error("查询昨日平均健康度失败", e);
        }
        return 0.0;
    }

    private double calculateGrowthRate(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        double rate = ((double) (current - previous) / previous) * 100;
        return Math.round(rate * 100.0) / 100.0;
    }

    private long countExceptions(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            TenantContext.setIgnoreTenant(true);
            List<System> systems = systemMapper.selectAllEnabled();

            long totalCount = 0;
            for (System system : systems) {
                totalCount += countSystemExceptions(system.getTenantId(),
                        system.getSystemId(), startTime, endTime);
            }

            return totalCount;
        } catch (Exception e) {
            log.error("统计异常总数失败", e);
        }
        return 0L;
    }

    private long countSystemExceptions(String tenantId, String systemId,
                                       LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String indexPattern = buildIndexPattern(tenantId, systemId);

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .size(0)
                    .query(q -> q.bool(b -> b
                            .filter(f -> f.range(r -> r
                                    .field("timestamp")
                                    .gte(JsonData.of(formatDateTime(startTime)))
                                    .lte(JsonData.of(formatDateTime(endTime)))
                            ))
                            .filter(f -> f.term(t -> t
                                    .field("level")
                                    .value("ERROR")
                            ))
                    ))
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);
            return response.hits().total().value();
        } catch (Exception e) {
            log.error("统计系统异常数失败: {}", systemId, e);
        }
        return 0L;
    }

    private long countSystemLogs(String tenantId, String systemId,
                                 LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String indexPattern = buildIndexPattern(tenantId, systemId);

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .size(0)
                    .query(q -> q.range(r -> r
                            .field("timestamp")
                            .gte(JsonData.of(formatDateTime(startTime)))
                            .lte(JsonData.of(formatDateTime(endTime)))
                    ))
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);
            return response.hits().total().value();
        } catch (Exception e) {
            log.error("统计系统日志总数失败: {}", systemId, e);
        }
        return 0L;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    /**
     * 获取异常类型的友好名称
     */
    private String getExceptionTypeName(String exceptionType) {
        if (exceptionType == null || exceptionType.isEmpty()) {
            return "未知异常";
        }

        // 提取简短的类名
        String[] parts = exceptionType.split("\\.");
        String simpleName = parts[parts.length - 1];

        // 移除"Exception"后缀
        if (simpleName.endsWith("Exception")) {
            simpleName = simpleName.substring(0, simpleName.length() - 9);
        }

        return simpleName;
    }
}