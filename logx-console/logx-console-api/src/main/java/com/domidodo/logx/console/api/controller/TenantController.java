package com.domidodo.logx.console.api.controller;


import com.domidodo.logx.common.result.Result;
import com.domidodo.logx.console.api.service.TenantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@Tag(name = "租户管理", description = "租户管理相关接口")
public class TenantController {

    private final TenantService tenantService;

    @GetMapping("/list")
    public Result<String> listTenants() {
        return Result.success("");
    }
}
