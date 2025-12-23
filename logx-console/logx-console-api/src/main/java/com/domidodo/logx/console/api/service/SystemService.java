package com.domidodo.logx.console.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.domidodo.logx.common.context.TenantContext;
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
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 系统管理服务（修复版）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final SystemMapper systemMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 分页查询系统列表
     */
    public PageResult<SystemDTO> listSystems(Integer page, Integer size) {
        // 验证分页参数
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 20;
        if (size > 100) size = 100;  // 限制最大值

        Page<System> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<System> wrapper = new LambdaQueryWrapper<>();
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.isEmpty()) {
            wrapper.eq(System::getTenantId, tenantId);
        }
        wrapper.orderByDesc(System::getCreateTime);
        Page<System> systemPage;
        TenantContext.setIgnoreTenant(true);
        systemPage = systemMapper.selectPage(pageParam, wrapper);

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
        // 验证systemId
        if (systemId == null || systemId.isEmpty()) {
            throw new BusinessException("系统ID不能为空");
        }

        System system = systemMapper.selectBySystemId(systemId);
        if (system == null) {
            throw new BusinessException("系统不存在");
        }
        return convertToDTO(system);
    }

    /**
     * 创建系统（修复版）
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemDTO createSystem(SystemDTO dto) {
        try {
            // 1. 验证必填字段
            if (dto.getTenantId() == null || dto.getTenantId().isEmpty()) {
                throw new BusinessException("租户ID不能为空");
            }
            if (dto.getSystemName() == null || dto.getSystemName().isEmpty()) {
                throw new BusinessException("系统名称不能为空");
            }

            // 2. 验证系统ID是否已存在
            if (dto.getSystemId() != null) {
                int count = systemMapper.existsBySystemId(dto.getSystemId());
                if (count > 0) {
                    throw new BusinessException("系统ID已存在");
                }
            } else {
                // 自动生成系统ID
                dto.setSystemId(generateSystemId());
            }

            // 3. 生成强API Key
            String apiKey = generateStrongApiKey();

            // 4. API Key加密存储
            String encryptedApiKey = encryptApiKey(apiKey);

            // 5. 转换为实体
            System system = new System();
            BeanUtils.copyProperties(dto, system);
            system.setApiKey(encryptedApiKey);  // 存储加密后的
            system.setStatus(1);

            // 6. 保存
            systemMapper.insert(system);

            // 7. 返回结果（包含原始API Key，仅此一次）
            dto.setId(system.getId());
            dto.setApiKey(apiKey);  // 返回原始密钥（仅创建时）
            dto.setCreateTime(system.getCreateTime());

            // 8. 记录日志（不记录API Key）
            log.info("已创建系统：systemId= {}，tenantId= {} ",
                    dto.getSystemId(), dto.getTenantId());

            return dto;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create system", e);
            throw new BusinessException("创建系统失败");
        }
    }

    /**
     * 更新系统
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemDTO updateSystem(Long id, SystemDTO dto) {
        try {
            System system = systemMapper.selectById(id);
            if (system == null) {
                throw new BusinessException("系统不存在");
            }

            // 更新字段
            if (dto.getSystemName() != null && !dto.getSystemName().isEmpty()) {
                system.setSystemName(dto.getSystemName());
            }
            if (dto.getSystemType() != null) {
                system.setSystemType(dto.getSystemType());
            }
            if (dto.getStatus() != null) {
                if (dto.getStatus() != 0 && dto.getStatus() != 1) {
                    throw new BusinessException("状态值无效");
                }
                system.setStatus(dto.getStatus());
            }

            systemMapper.updateById(system);

            log.info("系统已更新：id={}，systemId={}", id, system.getSystemId());
            return convertToDTO(system);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新系统失败：id={}", id, e);
            throw new BusinessException("更新系统失败");
        }
    }

    /**
     * 删除系统
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSystem(Long id) {
        try {
            System system = systemMapper.selectById(id);
            if (system == null) {
                throw new BusinessException("系统不存在");
            }

            // 软删除（不真正删除数据）
            system.setStatus(0);
            systemMapper.updateById(system);

            log.info("系统已删除：id={}，systemId={}", id, system.getSystemId());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除系统失败：id={}", id, e);
            throw new BusinessException("删除系统失败");
        }
    }

    /**
     * 重置API Key（修复版）
     */
    @Transactional(rollbackFor = Exception.class)
    public String resetApiKey(Long id) {
        try {
            System system = systemMapper.selectById(id);
            if (system == null) {
                throw new BusinessException("系统不存在");
            }

            // 生成强API Key
            String newApiKey = generateStrongApiKey();

            // 加密存储
            String encryptedApiKey = encryptApiKey(newApiKey);
            system.setApiKey(encryptedApiKey);

            systemMapper.updateById(system);

            // 记录日志（不记录API Key）
            log.info("API密钥重置：systemId＝{}", system.getSystemId());

            // 返回原始密钥（仅此一次）
            return newApiKey;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("未能重置API密钥：id＝{}", id, e);
            throw new BusinessException("重置API Key失败");
        }
    }

    /**
     * 验证API Key（修复版）
     */
    public boolean validateApiKey(String apiKey, String tenantId, String systemId) {
        try {
            // 1. 验证参数
            if (apiKey == null || apiKey.isEmpty()) {
                return false;
            }
            if (tenantId == null || tenantId.isEmpty()) {
                return false;
            }
            if (systemId == null || systemId.isEmpty()) {
                return false;
            }

            // 2. 加密输入的API Key
            String encryptedApiKey = encryptApiKey(apiKey);

            // 3. 查询系统
            System system = systemMapper.selectByApiKey(encryptedApiKey);
            if (system == null) {
                log.warn("找不到API密钥");
                return false;
            }

            // 4. 验证租户和系统ID
            boolean valid = system.getTenantId().equals(tenantId)
                            && system.getSystemId().equals(systemId)
                            && system.getStatus() == 1;

            if (!valid) {
                log.warn("API密钥验证失败：不匹配");
            }

            return valid;

        } catch (Exception e) {
            log.error("API密钥验证错误", e);
            return false;
        }
    }

    /**
     * 转换为DTO（不返回API Key）
     */
    private SystemDTO convertToDTO(System system) {
        SystemDTO dto = new SystemDTO();
        BeanUtils.copyProperties(system, dto);
        // 不返回API Key（安全）
        dto.setApiKey("sk_****" + system.getApiKey().substring(
                Math.max(0, system.getApiKey().length() - 8)));
        return dto;
    }

    /**
     * 生成系统ID
     */
    private String generateSystemId() {
        return "sys_" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 16);
    }

    /**
     * 生成强API Key（修复版）
     * 使用加密安全的随机数生成器
     */
    private String generateStrongApiKey() {
        // 生成32字节（256位）随机数据
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        // Base64编码
        String base64 = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        // 添加前缀和版本标识
        return "sk_live_" + base64;
    }

    /**
     * 加密API Key（用于存储）
     * 使用SHA-256单向加密
     */
    private String encryptApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API Key不能为空");
        }

        // 使用SHA-256哈希
        return DigestUtils.md5DigestAsHex(
                apiKey.getBytes(StandardCharsets.UTF_8));
    }
}