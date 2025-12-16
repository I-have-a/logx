package com.domidodo.logx.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LogLevelEnum {

    TRACE("TRACE", "跟踪", 1),
    DEBUG("DEBUG", "调试", 2),
    INFO("INFO", "信息", 3),
    WARN("WARN", "警告", 4),
    ERROR("ERROR", "错误", 5),
    FATAL("FATAL", "致命", 6);

    private final String code;
    private final String name;
    private final Integer level;

    public static LogLevelEnum fromCode(String code) {
        for (LogLevelEnum level : values()) {
            if (level.getCode().equalsIgnoreCase(code)) {
                return level;
            }
        }
        return INFO;
    }
}
