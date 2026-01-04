package com.domidodo.logx.engine.detection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.domidodo.logx.engine.detection.entity.Rule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
     * 增加触发次数（原子操作）
     */
    @Update("""
            UPDATE log_exception_rule
            SET trigger_count = COALESCE(trigger_count, 0) + 1,
                last_trigger_time = NOW()
            WHERE id = #{ruleId}""")
    int incrementTriggerCount(@Param("ruleId") Long ruleId);

    /**
     * 批量增加触发次数
     */
    @Update("""
            UPDATE log_exception_rule
            SET trigger_count = COALESCE(trigger_count, 0) + 1,
                last_trigger_time = NOW()
            WHERE id IN
            <foreach collection="ruleIds" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>""")
    int batchIncrementTriggerCount(@Param("ruleIds") List<Long> ruleIds);

    /**
     * 查询各类型规则数量
     */
    @Select("""
            SELECT
                rule_type,
                COUNT(*) as rule_count,
                SUM(COALESCE(trigger_count, 0)) as total_triggers
            FROM log_exception_rule
            WHERE status = 1
            GROUP BY rule_type""")
    List<Map<String, Object>> countAlertsByRuleType();

    /**
     * 查询最近触发的规则
     */
    @Select("""
            SELECT rule_name,
                   rule_type,
                   trigger_count,
                   last_trigger_time,
                   alert_level,
                   silence_period,
                   silence_scope
            FROM log_exception_rule
            WHERE status = 1
              AND last_trigger_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
            ORDER BY last_trigger_time DESC""")
    List<Map<String, Object>> getRecentTriggeredRules();

    /**
     * 查询高频触发规则
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
            ORDER BY triggers_per_hour DESC""")
    List<Map<String, Object>> getHighTriggeredRules();

    /**
     * 删除过期的规则快照
     */
    @Update("DELETE FROM log_rule_state_snapshot WHERE expire_time < NOW()")
    int deleteExpiredRuleSnapshots();

    /**
     * 重置月度触发次数
     */
    @Update("""
            UPDATE log_exception_rule
            SET trigger_count = 0,
                last_trigger_time = NULL
            WHERE MONTH(last_trigger_time) < MONTH(NOW())""")
    int resetMonthlyTriggerCount();

    /**
     * 禁用长期未触发的规则（超过30天）
     */
    @Update("""
            UPDATE log_exception_rule
            SET status = 0
            WHERE status = 1
              AND (last_trigger_time IS NULL OR last_trigger_time < DATE_SUB(NOW(), INTERVAL 30 DAY))
              AND create_time < DATE_SUB(NOW(), INTERVAL 30 DAY)""")
    int disableInactiveRules();

    /**
     * 更新规则静默期配置
     */
    @Update("""
            UPDATE log_exception_rule
            SET silence_period = #{silencePeriod},
                silence_scope = #{silenceScope},
                allow_escalation = #{allowEscalation},
                enable_aggregation = #{enableAggregation}
            WHERE id = #{ruleId}""")
    int updateSilenceConfig(@Param("ruleId") Long ruleId,
                            @Param("silencePeriod") Integer silencePeriod,
                            @Param("silenceScope") String silenceScope,
                            @Param("allowEscalation") Boolean allowEscalation,
                            @Param("enableAggregation") Boolean enableAggregation);
}