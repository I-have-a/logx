package com.domidodo.logx.sdk.spring.context;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户上下文提供器接口
 * 允许应用自定义用户信息获取方式
 */
public interface UserContextProvider {

    /**
     * 获取用户ID
     * @param request HTTP请求对象
     * @return 用户ID，如果无法获取返回null
     */
    String getUserId(HttpServletRequest request);

    /**
     * 获取用户名
     * @param request HTTP请求对象
     * @return 用户名，如果无法获取返回null
     */
    String getUserName(HttpServletRequest request);

    /**
     * 获取租户ID
     * @param request HTTP请求对象
     * @return 租户ID，如果无法获取返回null
     */
    default String getTenantId(HttpServletRequest request) {
        return null;
    }
}