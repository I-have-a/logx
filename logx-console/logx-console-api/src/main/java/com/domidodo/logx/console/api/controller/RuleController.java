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

import java.util.List;

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
            Page<Rule> pageParam = new Page<>(page, size);
            LambdaQueryWrapper<Rule> wrapper = new LambdaQueryWrapper<>();
            if (systemId != null && !systemId.isEmpty()) {
                wrapper.eq(Rule::getSystemId, systemId);
            }
            if (tenantId != null && !tenantId.isEmpty()) {
                wrapper.eq(Rule::getTenantId, tenantId);
            }
            wrapper.orderByDesc(Rule::getCreateTime);

            Page<Rule> rulePage = ruleMapper.selectPage(pageParam, wrapper);
            PageResult<Rule> result = PageResult.of(rulePage.getTotal(), rulePage.getRecords());
            return Result.success(result);
        } catch (Exception e) {
            log.error("List rules failed", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询规则详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询规则详情")
    public Result<Rule> getRule(@PathVariable Long id) {
        try {
            Rule rule = ruleMapper.selectById(id);
            if (rule == null) {
                return Result.error("规则不存在");
            }
            return Result.success(rule);
        } catch (Exception e) {
            log.error("Get rule failed: id={}", id, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 查询启用的规则
     */
    @GetMapping("/enabled")
    @Operation(summary = "查询启用的规则")
    public Result<List<Rule>> getEnabledRules() {
        try {
            List<Rule> rules = ruleMapper.selectEnabledRules(TenantContext.getTenantId(), TenantContext.getSystemId());
            return Result.success(rules);
        } catch (Exception e) {
            log.error("Get enabled rules failed", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 创建规则
     */
    @PostMapping
    @Operation(summary = "创建规则")
    public Result<Rule> createRule(@RequestBody Rule rule) {
        try {
            rule.setStatus(1); // 默认启用
            ruleMapper.insert(rule);
            log.info("Rule created: id={}, name={}", rule.getId(), rule.getRuleName());
            return Result.success(rule);
        } catch (Exception e) {
            log.error("Create rule failed", e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新规则
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新规则")
    public Result<Rule> updateRule(@PathVariable Long id, @RequestBody Rule rule) {
        try {
            Rule existing = ruleMapper.selectById(id);
            if (existing == null) {
                return Result.error("规则不存在");
            }

            rule.setId(id);
            ruleMapper.updateById(rule);
            log.info("Rule updated: id={}", id);
            return Result.success(rule);
        } catch (Exception e) {
            log.error("Update rule failed: id={}", id, e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除规则")
    public Result<Void> deleteRule(@PathVariable Long id) {
        try {
            ruleMapper.deleteById(id);
            log.info("Rule deleted: id={}", id);
            return Result.success();
        } catch (Exception e) {
            log.error("Delete rule failed: id={}", id, e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用规则
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "启用/禁用规则")
    public Result<Void> updateRuleStatus(@PathVariable Long id, @RequestParam Integer status) {
        try {
            Rule rule = ruleMapper.selectById(id);
            if (rule == null) {
                return Result.error("规则不存在");
            }

            rule.setStatus(status);
            ruleMapper.updateById(rule);
            log.info("Rule status updated: id={}, status={}", id, status);
            return Result.success();
        } catch (Exception e) {
            log.error("Update rule status failed: id={}", id, e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
}