package com.domidodo.logx.console.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨系统查询请求DTO
 */
@Data
public class CrossSystemQueryDTO {
    /**
     * 目标系统ID列表
     */
    private List<String> systemIds;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 日志级别
     */
    private String level;

    /**
     * 关键字
     */
    private String keyword;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 模块
     */
    private String module;

    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 20;
}
