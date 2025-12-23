package com.domidodo.logx.console.api.dto;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 跨系统日志DTO
 */
@Data
public class CrossSystemLogDTO {
    private String systemId;
    private String systemName;
    private String tenantId;
    private LocalDateTime timestamp;
    private String level;
    private String message;
    private String module;
    private String operation;
    private String userId;
    private String userName;
    private Long responseTime;
    private String traceId;
}
