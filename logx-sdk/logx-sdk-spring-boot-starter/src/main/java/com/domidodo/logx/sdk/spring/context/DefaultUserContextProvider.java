package com.domidodo.logx.sdk.spring.context;

import com.domidodo.logx.sdk.spring.properties.LogXProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

import java.security.Principal;

/**
 * 默认用户上下文提供器
 * 支持多种获取方式：请求头、Session、Principal
 */
@Slf4j
public class DefaultUserContextProvider implements UserContextProvider {

    private final LogXProperties.UserContext config;

    public DefaultUserContextProvider(LogXProperties.UserContext config) {
        this.config = config;
    }

    @Override
    public String getUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String userId;

        // 1. 尝试从请求头获取
        if (config.getSource().contains("header")) {
            userId = request.getHeader(config.getUserIdHeader());
            if (userId != null && !userId.isEmpty()) {
                return userId;
            }
        }

        // 2. 尝试从Session获取
        if (config.getSource().contains("session")) {
            try {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    Object userIdObj = session.getAttribute(config.getUserIdSessionKey());
                    if (userIdObj != null) {
                        return userIdObj.toString();
                    }
                }
            } catch (Exception e) {
                log.debug("无法从会话中获取用户ID", e);
            }
        }

        // 3. 尝试从Principal获取
        if (config.getSource().contains("principal")) {
            try {
                Principal principal = request.getUserPrincipal();
                if (principal != null) {
                    return principal.getName();
                }
            } catch (Exception e) {
                log.debug("无法从主体获取用户ID", e);
            }
        }

        // 4. 尝试从请求参数获取
        if (config.getSource().contains("parameter")) {
            userId = request.getParameter(config.getUserIdParameter());
            if (userId != null && !userId.isEmpty()) {
                return userId;
            }
        }

        return null;
    }

    @Override
    public String getUserName(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String userName;

        // 1. 尝试从请求头获取
        if (config.getSource().contains("header")) {
            userName = request.getHeader(config.getUserNameHeader());
            if (userName != null && !userName.isEmpty()) {
                return userName;
            }
        }

        // 2. 尝试从Session获取
        if (config.getSource().contains("session")) {
            try {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    Object userNameObj = session.getAttribute(config.getUserNameSessionKey());
                    if (userNameObj != null) {
                        return userNameObj.toString();
                    }
                }
            } catch (Exception e) {
                log.debug("无法从会话中获取用户名", e);
            }
        }

        // 3. 尝试从Principal获取
        if (config.getSource().contains("principal")) {
            try {
                Principal principal = request.getUserPrincipal();
                if (principal != null) {
                    return principal.getName();
                }
            } catch (Exception e) {
                log.debug("无法从主体获取用户名", e);
            }
        }

        return null;
    }

    @Override
    public String getTenantId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // 1. 尝试从请求头获取
        if (config.getSource().contains("header")) {
            String tenantId = request.getHeader(config.getTenantIdHeader());
            if (tenantId != null && !tenantId.isEmpty()) {
                return tenantId;
            }
        }

        // 2. 尝试从Session获取
        if (config.getSource().contains("session")) {
            try {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    Object tenantIdObj = session.getAttribute(config.getTenantIdSessionKey());
                    if (tenantIdObj != null) {
                        return tenantIdObj.toString();
                    }
                }
            } catch (Exception e) {
                log.debug("无法从会话中获取tenantId", e);
            }
        }

        return null;
    }
}