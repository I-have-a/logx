package com.domidodo.logx.console.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.domidodo.logx.console.api.entity.System;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统 Mapper（增强版）
 */
@Mapper
public interface SystemMapper extends BaseMapper<System> {

    /**
     * 根据 API Key 查询系统
     */
    @Select("""
            SELECT *
            FROM sys_system
            WHERE api_key = #{apiKey}
              AND status = 1""")
    System selectByApiKey(@Param("apiKey") String apiKey);

    /**
     * 根据系统ID查询
     */
    @Select("""
            SELECT *
            FROM sys_system
            WHERE system_id = #{systemId}
              AND status = 1""")
    System selectBySystemId(@Param("systemId") String systemId);

    /**
     * 检查系统ID是否存在
     */
    @Select("""
            SELECT COUNT(*)
            FROM sys_system
            WHERE system_id = #{systemId}""")
    int existsBySystemId(@Param("systemId") String systemId);

    // ==================== 监控后台新增方法 ====================

    /**
     * 查询所有启用的系统
     */
    @Select("""
            SELECT *
            FROM sys_system
            WHERE status = 1
            ORDER BY create_time DESC""")
    List<System> selectAllEnabled();

    /**
     * 根据租户ID查询系统列表
     */
    @Select("""
            SELECT *
            FROM sys_system
            WHERE tenant_id = #{tenantId}
              AND status = 1
            ORDER BY create_time DESC""")
    List<System> selectByTenantId(@Param("tenantId") String tenantId);

    /**
     * 统计接入系统总数
     */
    @Select("""
            SELECT COUNT(*)
            FROM sys_system
            WHERE status = 1""")
    int countEnabled();

    /**
     * 统计本月新增系统数
     */
    @Select("""
            SELECT COUNT(*)
            FROM sys_system
            WHERE status = 1
              AND DATE_FORMAT(create_time, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m')""")
    int countNewThisMonth();

    /**
     * 查询租户的系统数量
     */
    @Select("""
            SELECT COUNT(*)
            FROM sys_system
            WHERE tenant_id = #{tenantId}
              AND status = 1""")
    int countByTenantId(@Param("tenantId") String tenantId);
}