package com.domidodo.logx.console.api.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.domidodo.logx.common.dto.LogDTO;
import com.domidodo.logx.common.dto.QueryDTO;
import com.domidodo.logx.common.exception.BusinessException;
import com.domidodo.logx.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 日志查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogQueryService {

    private final ElasticsearchClient elasticsearchClient;

    /**
     * 索引前缀
     */
    private static final String INDEX_PREFIX = "logx-logs-";

    /**
     * ISO 8601格式化器
     */
    private static final DateTimeFormatter ES_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /**
     * 最大查询条数
     */
    private static final int MAX_QUERY_SIZE = 1000;

    /**
     * 最大时间范围（天）
     */
    private static final int MAX_TIME_RANGE_DAYS = 30;

    /**
     * 分页查询日志（修复版）
     */
    public PageResult<LogDTO> queryLogs(QueryDTO queryDTO) {
        try {
            // 1. 参数验证
            validateQueryDTO(queryDTO);

            // 2. 构建索引名称
            String indexPattern = buildIndexPattern(queryDTO);

            // 3. 构建查询条件
            Query query = buildQuery(queryDTO);

            // 4. 计算分页参数
            int from = (queryDTO.getPage() - 1) * queryDTO.getSize();
            int size = Math.min(queryDTO.getSize(), MAX_QUERY_SIZE);

            // 5. 构建搜索请求
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .query(query)
                    .from(from)
                    .size(size)
                    .timeout("30s")  // 添加超时
                    .sort(sort -> sort
                            .field(f -> f
                                    .field(queryDTO.getSortField())
                                    .order("asc".equalsIgnoreCase(queryDTO.getSortOrder())
                                            ? SortOrder.Asc : SortOrder.Desc)
                            )
                    )
            );

            // 6. 执行查询
            SearchResponse<Map> response = elasticsearchClient.search(
                    searchRequest, Map.class);

            // 7. 解析结果
            List<LogDTO> logs = new ArrayList<>();
            if (response.hits().hits() != null) {
                for (Hit<Map> hit : response.hits().hits()) {
                    try {
                        logs.add(convertToLogDTO(hit.source()));
                    } catch (Exception e) {
                        log.warn("转换日志失败：{}", hit.id(), e);
                    }
                }
            }

            // 8. 获取总数
            long total = response.hits().total() != null ?
                    response.hits().total().value() : 0;

            return PageResult.of(total, logs);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询日志失败", e);
            throw new BusinessException("查询失败，请稍后重试");
        }
    }

    /**
     * 根据 TraceId 查询日志
     */
    public List<LogDTO> queryByTraceId(String traceId) {
        try {
            // 验证TraceId
            if (traceId == null || traceId.isEmpty()) {
                throw new BusinessException("TraceId不能为空");
            }
            if (traceId.length() > 64) {
                throw new BusinessException("TraceId格式错误");
            }

            Query query = Query.of(q -> q
                    .term(t -> t
                            .field("traceId.keyword")  // 使用keyword字段
                            .value(traceId)
                    )
            );

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_PREFIX + "*")
                    .query(query)
                    .size(MAX_QUERY_SIZE)
                    .timeout("30s")
                    .sort(sort -> sort.field(f -> f
                            .field("timestamp")
                            .order(SortOrder.Asc)))
            );

            SearchResponse<Map> response = elasticsearchClient.search(
                    searchRequest, Map.class);

            List<LogDTO> logs = new ArrayList<>();
            if (response.hits().hits() != null) {
                for (Hit<Map> hit : response.hits().hits()) {
                    try {
                        logs.add(convertToLogDTO(hit.source()));
                    } catch (Exception e) {
                        log.warn("转换日志失败：{}", hit.id(), e);
                    }
                }
            }

            return logs;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("按traceId查询日志失败：{}", traceId, e);
            throw new BusinessException("查询失败，请稍后重试");
        }
    }

    /**
     * 验证查询参数
     */
    private void validateQueryDTO(QueryDTO queryDTO) {
        // 验证时间范围
        if (queryDTO.getStartTime() != null && queryDTO.getEndTime() != null) {
            Duration duration = Duration.between(
                    queryDTO.getStartTime(),
                    queryDTO.getEndTime());

            if (duration.isNegative()) {
                throw new BusinessException("开始时间不能晚于结束时间");
            }

            if (duration.toDays() > MAX_TIME_RANGE_DAYS) {
                throw new BusinessException(
                        "时间范围不能超过" + MAX_TIME_RANGE_DAYS + "天");
            }
        }

        // 验证分页参数
        if (queryDTO.getPage() == null || queryDTO.getPage() < 1) {
            queryDTO.setPage(1);
        }
        if (queryDTO.getSize() == null || queryDTO.getSize() < 1) {
            queryDTO.setSize(20);
        }
        if (queryDTO.getSize() > MAX_QUERY_SIZE) {
            queryDTO.setSize(MAX_QUERY_SIZE);
        }

        // 验证关键字长度
        if (queryDTO.getKeyword() != null) {
            if (queryDTO.getKeyword().length() > 200) {
                throw new BusinessException("关键字长度不能超过200个字符");
            }
            // 清理特殊字符
            queryDTO.setKeyword(sanitizeKeyword(queryDTO.getKeyword()));
        }

        // 验证userName
        if (queryDTO.getUserName() != null) {
            if (queryDTO.getUserName().length() > 50) {
                throw new BusinessException("用户名长度不能超过50个字符");
            }
            // 移除通配符
            queryDTO.setUserName(
                    queryDTO.getUserName().replace("*", "").replace("?", ""));
        }
    }

    /**
     * 清理关键字中的特殊字符
     */
    private String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        // 移除ES特殊字符
        return keyword.replaceAll("[+\\-=&|><!(){}\\[\\]^\"~*?:\\\\/]", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /**
     * 构建索引模式
     */
    private String buildIndexPattern(QueryDTO queryDTO) {
        StringBuilder pattern = new StringBuilder(INDEX_PREFIX);

        // 添加租户ID
        if (queryDTO.getTenantId() != null && !queryDTO.getTenantId().isEmpty()) {
            // 清理租户ID
            String sanitized = queryDTO.getTenantId()
                    .toLowerCase()
                    .replaceAll("[^a-z0-9-]", "");
            pattern.append(sanitized).append("-");
        } else {
            pattern.append("*-");
        }

        // 添加系统ID
        if (queryDTO.getSystemId() != null && !queryDTO.getSystemId().isEmpty()) {
            // 清理系统ID
            String sanitized = queryDTO.getSystemId()
                    .toLowerCase()
                    .replaceAll("[^a-z0-9-]", "");
            pattern.append(sanitized).append("-");
        } else {
            pattern.append("*-");
        }

        // 添加日期范围
        pattern.append("*");

        return pattern.toString();
    }

    /**
     * 构建查询条件（修复版）
     */
    private Query buildQuery(QueryDTO queryDTO) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // 时间范围（使用正确的格式）
        if (queryDTO.getStartTime() != null || queryDTO.getEndTime() != null) {
            boolQuery.filter(f -> f.range(r -> {
                var rangeQuery = r.field("timestamp");
                if (queryDTO.getStartTime() != null) {
                    rangeQuery.gte(JsonData.of(formatDateTime(queryDTO.getStartTime())));
                }
                if (queryDTO.getEndTime() != null) {
                    rangeQuery.lte(JsonData.of(formatDateTime(queryDTO.getEndTime())));
                }
                return rangeQuery;
            }));
        }

        // 日志级别
        if (queryDTO.getLevel() != null) {
            boolQuery.filter(f -> f.term(t -> t
                    .field("level")
                    .value(queryDTO.getLevel())));
        }

        // 用户ID
        if (queryDTO.getUserId() != null) {
            boolQuery.filter(f -> f.term(t -> t
                    .field("userId.keyword")
                    .value(queryDTO.getUserId())));
        }

        // 用户名（使用match而不是wildcard）
        if (queryDTO.getUserName() != null && !queryDTO.getUserName().isEmpty()) {
            boolQuery.filter(f -> f.match(m -> m
                    .field("userName")
                    .query(queryDTO.getUserName())));
        }

        // 功能模块
        if (queryDTO.getModule() != null) {
            boolQuery.filter(f -> f.term(t -> t
                    .field("module")
                    .value(queryDTO.getModule())));
        }

        // 操作类型
        if (queryDTO.getOperation() != null) {
            boolQuery.filter(f -> f.term(t -> t
                    .field("operation")
                    .value(queryDTO.getOperation())));
        }

        // 关键字搜索（已清理）
        if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().isEmpty()) {
            boolQuery.must(m -> m.match(ma -> ma
                    .field("message")
                    .query(queryDTO.getKeyword())
                    .fuzziness("AUTO")));  // 支持模糊搜索
        }

        // TraceId
        if (queryDTO.getTraceId() != null) {
            boolQuery.filter(f -> f.term(t -> t
                    .field("traceId.keyword")
                    .value(queryDTO.getTraceId())));
        }

        // 响应时间范围
        if (queryDTO.getMinResponseTime() != null ||
            queryDTO.getMaxResponseTime() != null) {
            boolQuery.filter(f -> f.range(r -> {
                var rangeQuery = r.field("responseTime");
                if (queryDTO.getMinResponseTime() != null) {
                    rangeQuery.gte(JsonData.of(queryDTO.getMinResponseTime()));
                }
                if (queryDTO.getMaxResponseTime() != null) {
                    rangeQuery.lte(JsonData.of(queryDTO.getMaxResponseTime()));
                }
                return rangeQuery;
            }));
        }

        return Query.of(q -> q.bool(boolQuery.build()));
    }

    /**
     * 格式化日期时间
     */
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneOffset.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(ES_DATETIME_FORMATTER);
    }

    /**
     * 转换为 LogDTO
     */
    private LogDTO convertToLogDTO(Map<String, Object> source) {
        LogDTO dto = new LogDTO();

        try {
            //字符串类型
            dto.setTraceId(getString(source, "traceId"));
            dto.setSpanId(getString(source, "spanId"));
            dto.setTenantId(getString(source, "tenantId"));
            dto.setSystemId(getString(source, "systemId"));
            dto.setLevel(getString(source, "level"));
            dto.setLogger(getString(source, "logger"));
            dto.setThread(getString(source, "thread"));
            dto.setClassName(getString(source, "className"));
            dto.setMethodName(getString(source, "methodName"));
            dto.setMessage(getString(source, "message"));
            dto.setException(getString(source, "exception"));
            dto.setUserId(getString(source, "userId"));
            dto.setUserName(getString(source, "userName"));
            dto.setModule(getString(source, "module"));
            dto.setOperation(getString(source, "operation"));
            dto.setRequestUrl(getString(source, "requestUrl"));
            dto.setRequestMethod(getString(source, "requestMethod"));
            dto.setRequestParams(getString(source, "requestParams"));
            dto.setIp(getString(source, "ip"));
            dto.setUserAgent(getString(source, "userAgent"));

            // 数字类型（安全转换）
            dto.setResponseTime(getLong(source, "responseTime"));
            dto.setLineNumber(getInteger(source, "lineNumber"));

            // 时间戳
            dto.setTimestamp(getLocalDateTime(source, "timestamp"));

        } catch (Exception e) {
            log.error("Failed to convert log DTO", e);
        }

        return dto;
    }

    /**
     * 安全获取字符串
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 安全获取Long
     */
    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全获取Integer
     */
    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全获取LocalDateTime
     */
    private LocalDateTime getLocalDateTime(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;

        try {
            if (value instanceof String) {
                return LocalDateTime.parse((String) value);
            } else if (value instanceof Number) {
                long millis = ((Number) value).longValue();
                return LocalDateTime.ofEpochSecond(
                        millis / 1000,
                        (int) ((millis % 1000) * 1000000),
                        ZoneOffset.UTC);
            }
        } catch (Exception e) {
            log.warn("未能解析时间戳：{}", value);
        }

        return null;
    }
}