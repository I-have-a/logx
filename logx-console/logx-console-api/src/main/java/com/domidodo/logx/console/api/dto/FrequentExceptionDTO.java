package com.domidodo.logx.console.api.dto;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 高频异常DTO
 */
@Data
public class FrequentExceptionDTO {
    private String alertType;
    private String ruleName;
    private Long count;
    private String systemId;
    private String systemName;
    private LocalDateTime latestTriggerTime;
}