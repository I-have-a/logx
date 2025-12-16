package com.domidodo.logx.engine.detection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.domidodo.logx.engine.detection.entity.Rule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 规则 Mapper（Detection 模块）
 */
@Mapper
public interface RuleMapper extends BaseMapper<Rule> {

    /**
     * 查询所有启用的规则
     */
    @Select("SELECT * FROM log_exception_rule WHERE status = 1 ORDER BY id")
    List<Rule> selectAllEnabledRules();

    /**
     * 根据租户和系统查询启用的规则
     */
    @Select("SELECT * FROM log_exception_rule WHERE tenant_id = #{tenantId} " +
            "AND system_id = #{systemId} AND status = 1")
    List<Rule> selectEnabledRulesBySystem(@Param("tenantId") String tenantId,
                                          @Param("systemId") String systemId);

    /**
     * 根据规则类型查询
     */
    @Select("SELECT * FROM log_exception_rule WHERE rule_type = #{ruleType} AND status = 1")
    List<Rule> selectEnabledRulesByType(@Param("ruleType") String ruleType);
}