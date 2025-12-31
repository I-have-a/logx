package com.domidodo.logx.common.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class LogDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String tenantId;

    private String systemId;

    private String systemName;

    private String module;

    private String level;

    private String message;

    private String userId;

    private String requestId;

    private String traceId;

    private String spanId;

    private Map<String, Object> context;

    private LocalDateTime timestamp;

    private Long responseTime;

    private String exceptionType;

    private String stackTrace;

    private String logger;

    private String thread;

    private String className;

    private String methodName;

    private String exception;

    private String userName;

    private String operation;

    private String requestUrl;

    private String requestMethod;

    private String requestParams;

    private String ip;

    private String userAgent;

    private Integer lineNumber;

    private List<String> tags;
}