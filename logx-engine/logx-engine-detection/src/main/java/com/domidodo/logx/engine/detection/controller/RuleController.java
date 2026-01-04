package com.domidodo.logx.engine.detection.controller;

import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.engine.detection.alerts.AlertService;
import com.domidodo.logx.engine.detection.alerts.AlertSilenceManager;
import com.domidodo.logx.engine.detection.entity.Rule;
import com.domidodo.logx.engine.detection.mapper.RuleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 规则管理控制器
 * 提供规则和静默期管理的 API
 */
@Slf4j
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Tag(name = "规则管理", description = "告警规则和静默期管理接口")
public class RuleController {

    private final RuleMapper ruleMapper;
    private final AlertService alertService;
    private final AlertSilenceManager silenceManager;

    // ==================== 规则管理 ====================

    /**
     * 查询规则列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询规则列表", description = "根据租户和系统查询启用的规则")
    public Result<List<Rule>> listRules(
            @Parameter(description = "租户ID") @RequestParam String tenantId,
            @Parameter(description = "系统ID") @RequestParam String systemId) {
        List<Rule> rules = ruleMapper.selectEnabledRulesBySystem(tenantId, systemId);
        return Result.success(rules);
    }

    /**
     * 查询规则详情
     */
    @GetMapping("/{ruleId}")
    @Operation(summary = "查询规则详情")
    public Result<Rule> getRule(@PathVariable Long ruleId) {
        Rule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            return Result.error("规则不存在");
        }
        return Result.success(rule);
    }

    // ==================== 静默期管理 ====================

    /**
     * 更新规则静默期配置
     */
    @PutMapping("/{ruleId}/silence")
    @Operation(summary = "更新静默期配置", description = "配置规则的静默时长、粒度等")
    public Result<Void> updateSilenceConfig(
            @PathVariable Long ruleId,
            @RequestBody @Validated SilenceConfigRequest request) {

        Rule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            return Result.error("规则不存在");
        }

        // 验证静默粒度
        String scope = request.getSilenceScope();
        if (scope != null && !List.of("RULE", "TARGET", "USER").contains(scope.toUpperCase())) {
            return Result.error("无效的静默粒度，支持：RULE, TARGET, USER");
        }

        // 验证静默时长
        if (request.getSilencePeriod() != null && request.getSilencePeriod() < 0) {
            return Result.error("静默时长不能为负数");
        }

        // 更新配置
        ruleMapper.updateSilenceConfig(
                ruleId,
                request.getSilencePeriod(),
                request.getSilenceScope(),
                request.getAllowEscalation(),
                request.getEnableAggregation()
        );

        log.info("已更新规则静默配置: ruleId={}, config={}", ruleId, request);
        return Result.success();
    }

    /**
     * 重置规则静默期
     */
    @PostMapping("/{ruleId}/silence/reset")
    @Operation(summary = "重置静默期", description = "立即清除规则的静默状态，允许再次告警")
    public Result<Void> resetRuleSilence(@PathVariable Long ruleId) {
        Rule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            return Result.error("规则不存在");
        }

        alertService.resetRuleSilence(ruleId);
        log.info("已重置规则静默期: ruleId={}", ruleId);
        return Result.success();
    }

    /**
     * 获取静默状态统计
     */
    @GetMapping("/silence/statistics")
    @Operation(summary = "获取静默状态统计", description = "返回当前活跃的静默数量和被抑制的告警数")
    public Result<Map<String, Object>> getSilenceStatistics() {
        Map<String, Object> stats = alertService.getSilenceStatistics();
        return Result.success(stats);
    }

    // ==================== 触发统计 ====================

    /**
     * 查询最近触发的规则
     */
    @GetMapping("/triggered/recent")
    @Operation(summary = "查询最近触发的规则", description = "返回最近1小时内触发的规则")
    public Result<List<Map<String, Object>>> getRecentTriggeredRules() {
        List<Map<String, Object>> rules = ruleMapper.getRecentTriggeredRules();
        return Result.success(rules);
    }

    /**
     * 查询高频触发规则
     */
    @GetMapping("/triggered/high-frequency")
    @Operation(summary = "查询高频触发规则", description = "返回触发次数超过阈值的规则")
    public Result<List<Map<String, Object>>> getHighTriggeredRules() {
        List<Map<String, Object>> rules = ruleMapper.getHighTriggeredRules();
        return Result.success(rules);
    }

    /**
     * 按规则类型统计
     */
    @GetMapping("/statistics/by-type")
    @Operation(summary = "按规则类型统计", description = "返回各规则类型的数量和触发次数")
    public Result<List<Map<String, Object>>> countByRuleType() {
        List<Map<String, Object>> stats = ruleMapper.countAlertsByRuleType();
        return Result.success(stats);
    }

    // ==================== 请求/响应对象 ====================

    /**
     * 静默配置请求
     */
    @Data
    public static class SilenceConfigRequest {
        /**
         * 静默时长（秒）
         * null 表示不修改
         */
        private Integer silencePeriod;

        /**
         * 静默粒度：RULE/TARGET/USER
         */
        private String silenceScope;

        /**
         * 是否允许升级突破静默期
         */
        private Boolean allowEscalation;

        /**
         * 是否启用告警聚合
         */
        private Boolean enableAggregation;
    }
}