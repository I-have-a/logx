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
     * 查询启用的规则
     */
    @Select("""
            SELECT *
            FROM log_exception_rule
            WHERE tenant_id = #{tenantId}
              AND system_id = #{systemId}
              AND status = 1""")
    List<Rule> selectEnabledRules(@Param("tenantId") Long tenantId, @Param("systemId") Long systemId);

    /**
     * 根据规则类型查询
     */
    @Select("""
            SELECT *
            FROM log_exception_rule
            WHERE tenant_id = #{tenantId}
              AND rule_type = #{ruleType}
              AND status = 1""")
    List<Rule> selectByRuleType(@Param("tenantId") String tenantId, @Param("ruleType") String ruleType);
}