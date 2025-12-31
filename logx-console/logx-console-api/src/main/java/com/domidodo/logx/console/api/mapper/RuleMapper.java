package com.domidodo.logx.console.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.domidodo.logx.console.api.entity.Rule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 规则 Mapper
 */
@Mapper
public interface RuleMapper extends BaseMapper<Rule> {

    /**
     * 查询启用的规则（修复类型错误）
     */
    @Select("""
            SELECT *
            FROM log_exception_rule
            WHERE tenant_id = #{tenantId}
              AND system_id = #{systemId}
              AND status = 1
            ORDER BY create_time DESC""")
    List<Rule> selectEnabledRules(
            @Param("tenantId") String tenantId,
            @Param("systemId") String systemId
    );

    /**
     * 根据规则类型查询
     */
    @Select("""
            SELECT *
            FROM log_exception_rule
            WHERE tenant_id = #{tenantId}
              AND rule_type = #{ruleType}
              AND status = 1
            ORDER BY create_time DESC""")
    List<Rule> selectByRuleType(
            @Param("tenantId") String tenantId,
            @Param("ruleType") String ruleType
    );

    /**
     * 查询租户下所有启用的规则
     */
    @Select("""
            SELECT *
            FROM log_exception_rule
            WHERE tenant_id = #{tenantId}
              AND status = 1
            ORDER BY create_time DESC""")
    List<Rule> selectEnabledRulesByTenant(
            @Param("tenantId") String tenantId
    );

    /**
     * 统计租户规则数量
     */
    @Select("""
            SELECT COUNT(*)
            FROM log_exception_rule
            WHERE tenant_id = #{tenantId}
              AND status = 1""")
    int countByTenant(@Param("tenantId") String tenantId);
}