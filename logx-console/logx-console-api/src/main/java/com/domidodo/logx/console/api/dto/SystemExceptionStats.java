package com.domidodo.logx.console.api.dto;

import lombok.Data;

/**
 * 系统异常统计
 */
@Data
public class SystemExceptionStats {
    private String systemId;
    private String systemName;
    private Long exceptionCount;
    private Double exceptionRate;
    private String trend; // UP/DOWN/STABLE
}