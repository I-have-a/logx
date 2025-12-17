package com.domidodo.logx.infrastructure.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.domidodo.logx.common.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 多租户处理器
 * 自动在 SQL 中添加 tenant_id 条件
 */
@Slf4j
@Component
public class MyTenantLineHandler implements TenantLineHandler {

    /**
     * 不需要租户隔离的表
     */
    private static final List<String> IGNORE_TABLES = Arrays.asList(
            "sys_tenant",           // 租户表本身不需要过滤
            "sys_user",             // 用户表（跨租户）
            "sys_role",             // 角色表（跨租户）
            "sys_permission"        // 权限表（跨租户）
    );

    /**
     * 获取租户ID
     */
    @Override
    public Expression getTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (TenantContext.isIgnoreTenant()) {
            return null; // 返回 null 则不添加租户条件
        }
        return new StringValue(tenantId);
    }

    /**
     * 获取租户字段名
     */
    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    /**
     * 是否忽略该表的租户过滤
     */
    @Override
    public boolean ignoreTable(String tableName) {
        // 转换为小写统一比较
        String lowerTableName = tableName.toLowerCase();

        if (TenantContext.isIgnoreTenant()) {
            return true;
        }

        // 如果在忽略列表中，返回 true
        boolean ignored = IGNORE_TABLES.stream()
                .anyMatch(table -> lowerTableName.contains(table.toLowerCase()));

        if (ignored) {
            log.debug("Ignoring tenant filter for table: {}", tableName);
        }

        return ignored;
    }
}