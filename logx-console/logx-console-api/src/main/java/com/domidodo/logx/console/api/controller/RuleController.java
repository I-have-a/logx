package com.domidodo.logx.console.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.domidodo.logx.common.context.TenantContext;
import com.domidodo.logx.common.result.PageResult;
import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.console.api.entity.Rule;
import com.domidodo.logx.console.api.mapper.RuleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Set;

/**
 * 规则管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Tag(name = "规则管理", description = "异常规则管理相关接口")
public class RuleController {

    private final RuleMapper ruleMapper;

    /**
     * 允许的规则类型
     */
    private static final Set<String> VALID_RULE_TYPES = Set.of(
            "RESPONSE_TIME",
            "ERROR_RATE",
            "CONTINUOUS_FAILURE",
            "FREQUENT_OPERATION"
    );

    /**
     * 允许的告警级别
     */
    private static final Set<String> VALID_ALERT_LEVELS = Set.of(
            "CRITICAL",
            "WARNING",
            "INFO"
    );

    /**
     * 分页查询规则列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询规则列表")
    public Result<PageResult<Rule>> listRules(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String systemId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        try {
            // 验证分页参数
            if (page < 1) page = 1;
            if (size < 1) size = 20;
            if (size > 100) size = 100;  // 限制最大值

            Page<Rule> pageParam = new Page<>(page, size);
            LambdaQueryWrapper<Rule> wrapper = new LambdaQueryWrapper<>();

            // 租户隔离
            if (tenantId != null && !tenantId.isEmpty()) {
                wrapper.eq(Rule::getTenantId, tenantId);
            } else {
                // 如果没有指定租户，使用当前上下文的租户
                TenantContext.setIgnoreTenant(true);
                String contextTenantId = TenantContext.getTenantId();
                if (contextTenantId != null) {
                    wrapper.eq(Rule::getTenantId, contextTenantId);
                }
            }

            if (systemId != null && !systemId.isEmpty()) {
                wrapper.eq(Rule::getSystemId, systemId);
            } else {
                // 如果没有指定系统，使用当前上下文的系统
                String contextSystemId = TenantContext.getSystemId();
                if (contextSystemId != null) {
                    wrapper.eq(Rule::getSystemId, contextSystemId);
                }
            }

            wrapper.orderByDesc(Rule::getCreateTime);

            Page<Rule> rulePage = ruleMapper.selectPage(pageParam, wrapper);
            PageResult<Rule> result = PageResult.of(
                    rulePage.getTotal(),
                    rulePage.getRecords());

            return Result.success(result);

        } catch (Exception e) {
            log.error("获取列表规则失败", e);
            return Result.error("查询失败");  // 不泄露详细错误
        }
    }

    /**
     * 根据ID查询规则详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询规则详情")
    public Result<Rule> getRule(@PathVariable Long id) {
        try {
            // 验证ID
            if (id == null || id <= 0) {
                return Result.error("ID无效");
            }

            Rule rule = ruleMapper.selectById(id);
            if (rule == null) {
                return Result.error("规则不存在");
            }

            // 验证权限（租户隔离）
            String contextTenantId = TenantContext.getTenantId();
            if (contextTenantId != null &&
                !contextTenantId.equals(rule.getTenantId())) {
                return Result.error("无权访问此规则");
            }

            return Result.success(rule);

        } catch (Exception e) {
            log.error("获取规则失败：id={}", id, e);
            return Result.error("查询失败");
        }
    }

    /**
     * 查询启用的规则
     */
    @GetMapping("/enabled")
    @Operation(summary = "查询启用的规则")
    public Result<List<Rule>> getEnabledRules(
            @RequestParam(required = false) String systemId) {
        try {
            // 从上下文获取租户ID和系统ID（String类型）
            String tenantId = TenantContext.getTenantId();

            if (systemId == null || systemId.isEmpty()) {
                systemId = TenantContext.getSystemId();
            }

            // 验证必填参数
            if (tenantId == null || tenantId.isEmpty()) {
                return Result.error("租户ID不能为空");
            }
            if (systemId == null || systemId.isEmpty()) {
                return Result.error("系统ID不能为空");
            }

            List<Rule> rules = ruleMapper.selectEnabledRules(tenantId, systemId);
            return Result.success(rules);

        } catch (Exception e) {
            log.error("获取启用规则失败", e);
            return Result.error("查询失败");
        }
    }

    /**
     * 创建规则
     */
    @PostMapping
    @Operation(summary = "创建规则")
    public Result<Rule> createRule(@Valid @RequestBody Rule rule) {
        try {
            // 验证必填字段
            if (rule.getTenantId() == null || rule.getTenantId().isEmpty()) {
                return Result.error("租户ID不能为空");
            }
            if (rule.getSystemId() == null || rule.getSystemId().isEmpty()) {
                return Result.error("系统ID不能为空");
            }
            if (rule.getRuleName() == null || rule.getRuleName().isEmpty()) {
                return Result.error("规则名称不能为空");
            }

            // 验证规则类型
            if (!VALID_RULE_TYPES.contains(rule.getRuleType())) {
                return Result.error("规则类型无效");
            }

            // 验证告警级别
            if (!VALID_ALERT_LEVELS.contains(rule.getAlertLevel())) {
                return Result.error("告警级别无效");
            }

            // 验证权限
            String contextTenantId = TenantContext.getTenantId();
            if (contextTenantId != null &&
                !contextTenantId.equals(rule.getTenantId())) {
                return Result.error("无权创建此租户的规则");
            }

            // 设置默认值
            if (rule.getStatus() == null) {
                rule.setStatus(1);
            }

            ruleMapper.insert(rule);

            log.info("已创建规则：id={}，name={}、tenant={}",
                    rule.getId(), rule.getRuleName(), rule.getTenantId());

            return Result.success(rule);

        } catch (Exception e) {
            log.error("创建规则失败", e);
            return Result.error("创建失败");
        }
    }

    /**
     * 更新规则
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新规则")
    public Result<Rule> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody Rule rule) {
        try {
            // 验证ID
            if (id == null || id <= 0) {
                return Result.error("ID无效");
            }

            Rule existing = ruleMapper.selectById(id);
            if (existing == null) {
                return Result.error("规则不存在");
            }

            // 验证权限
            String contextTenantId = TenantContext.getTenantId();
            if (contextTenantId != null &&
                !contextTenantId.equals(existing.getTenantId())) {
                return Result.error("无权修改此规则");
            }

            // 验证规则类型
            if (rule.getRuleType() != null &&
                !VALID_RULE_TYPES.contains(rule.getRuleType())) {
                return Result.error("规则类型无效");
            }

            // 验证告警级别
            if (rule.getAlertLevel() != null &&
                !VALID_ALERT_LEVELS.contains(rule.getAlertLevel())) {
                return Result.error("告警级别无效");
            }

            rule.setId(id);
            // 不允许修改租户ID
            rule.setTenantId(existing.getTenantId());

            ruleMapper.updateById(rule);

            log.info("规则已更新：id={}，租户={}", id, existing.getTenantId());

            return Result.success(rule);

        } catch (Exception e) {
            log.error("更新规则失败：id={}", id, e);
            return Result.error("更新失败");
        }
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除规则")
    public Result<Void> deleteRule(@PathVariable Long id) {
        try {
            // 验证ID
            if (id == null || id <= 0) {
                return Result.error("ID无效");
            }

            Rule existing = ruleMapper.selectById(id);
            if (existing == null) {
                return Result.error("规则不存在");
            }

            // 验证权限
            String contextTenantId = TenantContext.getTenantId();
            if (contextTenantId != null &&
                !contextTenantId.equals(existing.getTenantId())) {
                return Result.error("无权删除此规则");
            }

            // 软删除
            existing.setStatus(0);
            ruleMapper.updateById(existing);

            log.info("已删除规则：id={}，租户={}", id, existing.getTenantId());

            return Result.success();

        } catch (Exception e) {
            log.error("删除规则失败：id={}", id, e);
            return Result.error("删除失败");
        }
    }

    /**
     * 启用/禁用规则
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "启用/禁用规则")
    public Result<Void> updateRuleStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        try {
            // 验证参数
            if (id == null || id <= 0) {
                return Result.error("ID无效");
            }
            if (status != 0 && status != 1) {
                return Result.error("状态值无效");
            }

            Rule rule = ruleMapper.selectById(id);
            if (rule == null) {
                return Result.error("规则不存在");
            }

            // 验证权限
            String contextTenantId = TenantContext.getTenantId();
            if (contextTenantId != null &&
                !contextTenantId.equals(rule.getTenantId())) {
                return Result.error("无权修改此规则");
            }

            rule.setStatus(status);
            ruleMapper.updateById(rule);

            log.info("规则状态已更新：id={}，状态={}、租户={}",
                    id, status, rule.getTenantId());

            return Result.success();

        } catch (Exception e) {
            log.error("更新规则状态失败：id={}", id, e);
            return Result.error("更新失败");
        }
    }
}