package com.domidodo.logx.console.api.dto;

import lombok.Data;

/**
 * 异常类型统计
 */
@Data
public class ExceptionTypeStats {
    private String alertType;
    private String alertTypeName;
    private Long count;
    private Double percentage;
}
