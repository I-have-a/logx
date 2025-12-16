package com.domidodo.logx.common.enums;

import lombok.Getter;

/**
 * 告警级别枚举
 */
@Getter
public enum AlertLevelEnum {

    /**
     * 严重
     */
    CRITICAL("CRITICAL", "严重", 1),

    /**
     * 警告
     */
    WARNING("WARNING", "警告", 2),

    /**
     * 提示
     */
    INFO("INFO", "提示", 3);

    /**
     * 代码
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 级别（数值越小越严重）
     */
    private final Integer level;

    AlertLevelEnum(String code, String desc, Integer level) {
        this.code = code;
        this.desc = desc;
        this.level = level;
    }

    /**
     * 根据代码获取枚举
     */
    public static AlertLevelEnum fromCode(String code) {
        for (AlertLevelEnum level : values()) {
            if (level.getCode().equals(code)) {
                return level;
            }
        }
        return INFO;
    }

    /**
     * 判断是否需要立即通知
     */
    public boolean isImmediateNotify() {
        return this == CRITICAL;
    }
}