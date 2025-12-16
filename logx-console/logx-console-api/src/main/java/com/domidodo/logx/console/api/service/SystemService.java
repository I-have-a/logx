package com.domidodo.logx.console.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.domidodo.logx.common.dto.SystemDTO;
import com.domidodo.logx.common.exception.BusinessException;
import com.domidodo.logx.common.result.PageResult;
import com.domidodo.logx.console.api.entity.System;
import com.domidodo.logx.console.api.mapper.SystemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 系统管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final SystemMapper systemMapper;

    /**
     * 分页查询系统列表
     */
    public PageResult<SystemDTO> listSystems(String tenantId, Integer page, Integer size) {
        Page<System> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<System> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null && !tenantId.isEmpty()) {
            wrapper.eq(System::getTenantId, tenantId);
        }
        wrapper.orderByDesc(System::getCreateTime);

        Page<System> systemPage = systemMapper.selectPage(pageParam, wrapper);

        List<SystemDTO> dtoList = systemPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResult.of(systemPage.getTotal(), dtoList);
    }

    /**
     * 根据ID查询系统
     */
    public SystemDTO getSystemById(Long id) {
        System system = systemMapper.selectById(id);
        if (system == null) {
            throw new BusinessException("系统不存在");
        }
        return convertToDTO(system);
    }

    /**
     * 根据系统ID查询
     */
    public SystemDTO getSystemBySystemId(String systemId) {
        System system = systemMapper.selectBySystemId(systemId);
        if (system == null) {
            throw new BusinessException("系统不存在");
        }
        return convertToDTO(system);
    }

    /**
     * 创建系统
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemDTO createSystem(SystemDTO dto) {
        // 1. 验证系统ID是否已存在
        if (dto.getSystemId() != null) {
            int count = systemMapper.existsBySystemId(dto.getSystemId());
            if (count > 0) {
                throw new BusinessException("系统ID已存在");
            }
        } else {
            // 自动生成系统ID
            dto.setSystemId(generateSystemId());
        }

        // 2. 生成API Key
        String apiKey = generateApiKey();

        // 3. 转换为实体
        System system = new System();
        BeanUtils.copyProperties(dto, system);
        system.setApiKey(apiKey);
        system.setStatus(1); // 默认启用

        // 4. 保存
        systemMapper.insert(system);

        // 5. 返回结果
        dto.setId(system.getId());
        dto.setApiKey(apiKey);
        dto.setCreateTime(system.getCreateTime());

        log.info("System created: systemId={}, tenantId={}", dto.getSystemId(), dto.getTenantId());
        return dto;
    }

    /**
     * 更新系统
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemDTO updateSystem(Long id, SystemDTO dto) {
        System system = systemMapper.selectById(id);
        if (system == null) {
            throw new BusinessException("系统不存在");
        }

        // 更新字段
        if (dto.getSystemName() != null) {
            system.setSystemName(dto.getSystemName());
        }
        if (dto.getSystemType() != null) {
            system.setSystemType(dto.getSystemType());
        }
        if (dto.getStatus() != null) {
            system.setStatus(dto.getStatus());
        }

        systemMapper.updateById(system);

        log.info("System updated: id={}, systemId={}", id, system.getSystemId());
        return convertToDTO(system);
    }

    /**
     * 删除系统
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSystem(Long id) {
        System system = systemMapper.selectById(id);
        if (system == null) {
            throw new BusinessException("系统不存在");
        }

        systemMapper.deleteById(id);
        log.info("System deleted: id={}, systemId={}", id, system.getSystemId());
    }

    /**
     * 重置API Key
     */
    @Transactional(rollbackFor = Exception.class)
    public String resetApiKey(Long id) {
        System system = systemMapper.selectById(id);
        if (system == null) {
            throw new BusinessException("系统不存在");
        }

        String newApiKey = generateApiKey();
        system.setApiKey(newApiKey);
        systemMapper.updateById(system);

        log.info("API Key reset: systemId={}", system.getSystemId());
        return newApiKey;
    }

    /**
     * 验证API Key
     */
    public boolean validateApiKey(String apiKey, String tenantId, String systemId) {
        System system = systemMapper.selectByApiKey(apiKey);
        if (system == null) {
            return false;
        }

        return system.getTenantId().equals(tenantId)
               && system.getSystemId().equals(systemId)
               && system.getStatus() == 1;
    }

    /**
     * 转换为DTO
     */
    private SystemDTO convertToDTO(System system) {
        SystemDTO dto = new SystemDTO();
        BeanUtils.copyProperties(system, dto);
        return dto;
    }

    /**
     * 生成系统ID
     */
    private String generateSystemId() {
        return "sys_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 生成API Key
     */
    private String generateApiKey() {
        return "sk_" + UUID.randomUUID().toString().replace("-", "");
    }
}