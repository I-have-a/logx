package com.domidodo.logx.engine.detection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.domidodo.logx.engine.detection.entity.Rule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 规则 Mapper（Detection 模块）
 */
@Mapper
public interface RuleMapper extends BaseMapper<Rule> {

    /**
     * 查询所有启用的规则
     */
    @Select("""
            SELECT *
            FROM log_exception_rule
            WHERE status = 1
            ORDER BY id""")
    List<Rule> selectAllEnabledRules();

    /**
     * 根据租户和系统查询启用的规则
     */
    @Select("""
            SELECT *
            FROM log_exception_rule
            WHERE tenant_id = #{tenantId}
              AND system_id = #{systemId}
              AND status = 1""")
    List<Rule> selectEnabledRulesBySystem(@Param("tenantId") String tenantId,
                                          @Param("systemId") String systemId);

    /**
     * 根据规则类型查询
     */
    @Select("""
            SELECT *
            FROM log_exception_rule
            WHERE rule_type = #{ruleType}
              AND status = 1""")
    List<Rule> selectEnabledRulesByType(@Param("ruleType") String ruleType);

    /**
     * 查询各类型规则数量
     */
    @Select("""
            SELECT
                rule_type,
                COUNT(*) as rule_count,
                SUM(trigger_count) as total_triggers
            FROM log_exception_rule
            WHERE status = 1
            GROUP BY rule_type;""")
    List<Map<String, Object>> countAlertsByRuleType();

    /**
     * 查询最近触发的规则
     */
    @Select("""
            SELECT rule_name,
                   rule_type,
                   trigger_count,
                   last_trigger_time,
                   alert_level
            FROM log_exception_rule
            WHERE status = 1
              AND last_trigger_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
            ORDER BY last_trigger_time DESC;
            """)
    List<Map<String, Object>> getRecentTriggeredRules();

    /**
     * 批量标记为已读
     */
    @Select("""
            SELECT rule_name,
                   rule_type,
                   monitor_target,
                   trigger_count,
                   ROUND(trigger_count / TIMESTAMPDIFF(HOUR, create_time, NOW()), 2) as triggers_per_hour
            FROM log_exception_rule
            WHERE status = 1
              AND trigger_count > 100
              AND create_time < DATE_SUB(NOW(), INTERVAL 24 HOUR)
            ORDER BY triggers_per_hour DESC;"""
    )
    List<Map<String, Object>> getHighTriggeredRules();

    /**
     * 删除过期的规则快照
     */
    @Select("DELETE FROM log_rule_state_snapshot\n" +
            "WHERE expire_time < NOW();")
    void deleteExpiredRuleSnapshots();

    /**
     * 重置月度触发次数
     */
    @Select("""
            UPDATE log_exception_rule
            SET trigger_count = 0,
                last_trigger_time = NULL
            WHERE MONTH(last_trigger_time) < MONTH(NOW());""")
    void resetMonthlyTriggerCount();

    /**
     * 禁用长期未触发的规则（超过30天）
     */
    @Select("""
            UPDATE log_exception_rule
            SET status = 0
            WHERE status = 1
              AND (last_trigger_time IS NULL OR last_trigger_time < DATE_SUB(NOW(), INTERVAL 30 DAY))
              AND create_time < DATE_SUB(NOW(), INTERVAL 30 DAY);""")
    void deleteInvalidRules();
}