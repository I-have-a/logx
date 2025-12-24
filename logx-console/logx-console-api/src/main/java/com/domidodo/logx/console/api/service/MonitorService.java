package com.domidodo.logx.console.api.service;

import com.domidodo.logx.common.result.PageResult;
import com.domidodo.logx.console.api.dto.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控服务接口
 */
public interface MonitorService {

    /**
     * 获取监控总览
     */
    MonitorOverviewDTO getMonitorOverview();

    /**
     * 获取系统状态列表
     */
    PageResult<SystemStatusDTO> getSystemStatusList(Integer page, Integer size,
                                                    String keyword, String healthLevel);

    /**
     * 获取系统状态
     */
    SystemStatusDTO getSystemStatus(String systemId);

    /**
     * 跨系统日志查询
     */
    CrossSystemQueryResultDTO crossSystemQuery(CrossSystemQueryDTO queryDTO);

    /**
     * 导出跨系统日志
     */
    String exportCrossSystemLogs(CrossSystemQueryDTO queryDTO);

    /**
     * 获取可查询的系统列表
     */
    List<SystemStatusDTO> getQueryableSystems();

    /**
     * 获取全局异常监控
     */
    GlobalExceptionMonitorDTO getGlobalExceptionMonitor(LocalDateTime startTime,
                                                        LocalDateTime endTime);

    /**
     * 获取高频异常
     */
    List<FrequentExceptionDTO> getFrequentExceptions(Integer limit,
                                                     LocalDateTime startTime,
                                                     LocalDateTime endTime);

    /**
     * 获取异常趋势
     */
    List<ExceptionTrendPoint> getExceptionTrend(Integer days);

    /**
     * 按系统统计异常
     */
    List<SystemExceptionStats> getExceptionsBySystem(LocalDateTime startTime,
                                                     LocalDateTime endTime);

    /**
     * 按类型统计异常
     */
    List<ExceptionTypeStats> getExceptionsByType(LocalDateTime startTime,
                                                 LocalDateTime endTime);

    /**
     * 获取健康度分析
     */
    HealthAnalysisResultDTO getHealthAnalysis(HealthAnalysisRequestDTO requestDTO);

    /**
     * 获取系统健康度
     */
    SystemHealthDTO getSystemHealth(String systemId, Integer hours);

    /**
     * 获取健康度趋势
     */
    List<HealthTrendPoint> getHealthTrend(String systemId, Integer days);

    /**
     * 获取健康度分布
     */
    HealthDistribution getHealthDistribution();

    /**
     * 获取健康因素
     */
    List<HealthFactor> getHealthFactors(String systemId);

    /**
     * 刷新缓存
     */
    void refreshCache();
}