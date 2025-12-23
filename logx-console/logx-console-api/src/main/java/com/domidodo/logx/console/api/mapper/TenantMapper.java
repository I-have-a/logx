package com.domidodo.logx.console.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.domidodo.logx.console.api.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 租户 Mapper
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {

    /**
     * 根据租户ID查询
     */
    @Select("""
            SELECT *
            FROM sys_tenant
            WHERE tenant_id = #{tenantId}
              AND status = 1""")
    Tenant selectByTenantId(@Param("tenantId") String tenantId);

    /**
     * 查询所有启用的租户
     */
    @Select("""
            SELECT *
            FROM sys_tenant
            WHERE status = 1
            ORDER BY create_time DESC""")
    List<Tenant> selectAllEnabled();

    /**
     * 统计租户数量
     */
    @Select("""
            SELECT COUNT(*)
            FROM sys_tenant
            WHERE status = 1""")
    int countEnabled();

    /**
     * 统计本月新增租户数
     */
    @Select("""
            SELECT COUNT(*)
            FROM sys_tenant
            WHERE status = 1
              AND DATE_FORMAT(create_time, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m')""")
    int countNewThisMonth();
}