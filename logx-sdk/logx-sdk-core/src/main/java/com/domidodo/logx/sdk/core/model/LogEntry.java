package com.domidodo.logx.sdk.core.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;


import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {
    private String id;
    private Long tenantId;
    private Long systemId;
    private String systemName;
    private String level;
    private String message;
    private LocalDateTime timestamp;
    private String traceId;
    private String spanId;
    private Map<String, Object> context;
    private String exceptionType;
    private String stackTrace;
    private Long responseTime;
}
