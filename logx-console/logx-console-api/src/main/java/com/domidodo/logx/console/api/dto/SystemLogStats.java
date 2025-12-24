package com.domidodo.logx.console.api.dto;

import lombok.Data;

@Data
public class SystemLogStats {
    /**
     * 系统ID
     */
    private String systemId;

    /**
     * 系统名称
     */
    private String systemName;

    /**
     * 日志总数
     */
    private Long totalCount;

    /**
     * 异常日志数
     */
    private Long errorCount;

    /**
     * 警告日志数
     */
    private Long warnCount;

    /**
     * 平均响应时间
     */
    private Double avgResponseTime;

    /**
     * 最大响应时间
     */
    private Long maxResponseTime;

    /**
     * 最小响应时间
     */
    private Long minResponseTime;
}
