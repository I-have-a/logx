package com.domidodo.logx.infrastructure.interceptor;


import com.domidodo.logx.common.constant.SystemConstant;
import com.domidodo.logx.common.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // 1. 提取租户ID
        String tenantIdStr = request.getHeader(SystemConstant.TENANT_HEADER);
        if (StringUtils.hasText(tenantIdStr)) {
            try {
                TenantContext.setTenantId(tenantIdStr);
                log.debug("租户ID: {}", tenantIdStr);
            } catch (NumberFormatException e) {
                log.warn("无效的租户ID: {}", tenantIdStr);
            }
        }

        // 2. 提取用户ID
        String userIdStr = request.getHeader(SystemConstant.USER_HEADER);
        if (StringUtils.hasText(userIdStr)) {
            TenantContext.setUserId(Long.parseLong(userIdStr));
        }

        // 3. 生成或提取请求ID
        String requestId = request.getHeader(SystemConstant.REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        TenantContext.setRequestId(requestId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        TenantContext.clear();
    }
}
