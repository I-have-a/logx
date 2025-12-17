package com.domidodo.logx.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志查询DTO
 */
@Data
public class QueryDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 系统ID
     */
    private String systemId;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 日志级别
     */
    private String level;

    /**
     * 操作用户ID
     */
    private String userId;

    /**
     * 操作用户名
     */
    private String userName;

    /**
     * 功能模块
     */
    private String module;

    /**
     * 操作类型
     */
    private String operation;

    /**
     * 关键字搜索
     */
    private String keyword;

    /**
     * TraceId
     */
    private String traceId;

    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * 请求方法
     */
    private String requestMethod;

    /**
     * 响应时间范围 - 最小值(ms)
     */
    private Long minResponseTime;

    /**
     * 响应时间范围 - 最大值(ms)
     */
    private Long maxResponseTime;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 20;

    /**
     * 排序字段
     */
    private String sortField = "timestamp";

    /**
     * 排序方式：asc/desc
     */
    private String sortOrder = "desc";
}
