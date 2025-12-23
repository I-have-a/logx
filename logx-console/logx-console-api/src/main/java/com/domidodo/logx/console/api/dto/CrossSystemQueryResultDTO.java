package com.domidodo.logx.console.api.dto;


import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 跨系统查询响应DTO
 */
@Data
public class CrossSystemQueryResultDTO {
    /**
     * 总记录数
     */
    private Long total;

    /**
     * 日志列表
     */
    private List<CrossSystemLogDTO> logs;

    /**
     * 按系统分组的统计
     */
    private Map<String, SystemLogStats> systemStats;
}
