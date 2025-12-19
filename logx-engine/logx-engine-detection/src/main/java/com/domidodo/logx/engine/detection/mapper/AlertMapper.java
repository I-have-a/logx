package com.domidodo.logx.engine.detection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.domidodo.logx.engine.detection.entity.Alert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警记录 Mapper
 */
@Mapper
public interface AlertMapper extends BaseMapper<Alert> {

    /**
     * 查询待处理告警
     */
    @Select("""
            SELECT *
            FROM log_alert_record
            WHERE tenant_id = #{tenantId}
              AND status = 'PENDING'
            ORDER BY trigger_time DESC""")
    List<Alert> selectPendingAlerts(@Param("tenantId") String tenantId);

    /**
     * 查询最近的告警
     */
    @Select("""
            SELECT *
            FROM log_alert_record
            WHERE tenant_id = #{tenantId}
              AND system_id = #{systemId}
              AND trigger_time >= #{startTime}
            ORDER BY trigger_time DESC
            LIMIT #{limit}""")
    List<Alert> selectRecentAlerts(@Param("tenantId") String tenantId,
                                   @Param("systemId") String systemId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("limit") int limit);

    /**
     * 统计告警数量
     */
    @Select("""
            SELECT COUNT(*)
            FROM log_alert_record
            WHERE tenant_id = #{tenantId}
              AND trigger_time >= #{startTime}
              AND trigger_time <= #{endTime}""")
    long countAlerts(@Param("tenantId") String tenantId,
                     @Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime);
}