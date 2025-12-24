package com.domidodo.logx.gateway.grpc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ValidateMapper extends BaseMapper<Object> {

    @Select("""
            select count(*)
            from sys_system
            where api_key = #{apiKey}
              and tenant_id = #{tenantId}
              and system_id = #{systemId}""")
    int validateApiKey(String apiKey, String tenantId, String systemId);
}
