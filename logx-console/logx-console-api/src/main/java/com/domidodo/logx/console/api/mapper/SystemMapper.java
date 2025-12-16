package com.domidodo.logx.console.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.domidodo.logx.console.api.entity.System;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统 Mapper
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
}