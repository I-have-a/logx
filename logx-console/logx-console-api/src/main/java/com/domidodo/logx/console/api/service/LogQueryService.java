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
import com.domidodo.logx.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
     * 日期格式
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * 分页查询日志
     */
    public PageResult<LogDTO> queryLogs(QueryDTO queryDTO) {
        try {
            // 1. 构建索引名称（支持通配符）
            String indexPattern = buildIndexPattern(queryDTO);

            // 2. 构建查询条件
            Query query = buildQuery(queryDTO);

            // 3. 计算分页参数
            int from = (queryDTO.getPage() - 1) * queryDTO.getSize();
            int size = queryDTO.getSize();

            // 4. 构建搜索请求
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .query(query)
                    .from(from)
                    .size(size)
                    .sort(sort -> sort
                            .field(f -> f
                                    .field(queryDTO.getSortField())
                                    .order("asc".equalsIgnoreCase(queryDTO.getSortOrder())
                                            ? SortOrder.Asc : SortOrder.Desc)
                            )
                    )
            );

            // 5. 执行查询
            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            // 6. 解析结果
            List<LogDTO> logs = new ArrayList<>();
            if (response.hits().hits() != null) {
                for (Hit<Map> hit : response.hits().hits()) {
                    logs.add(convertToLogDTO(hit.source()));
                }
            }

            // 7. 获取总数
            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            return PageResult.of(total, logs);

        } catch (Exception e) {
            log.error("Failed to query logs", e);
            return PageResult.of(0L, new ArrayList<>());
        }
    }

    /**
     * 根据 TraceId 查询日志
     */
    public List<LogDTO> queryByTraceId(String traceId) {
        try {
            Query query = Query.of(q -> q
                    .term(t -> t
                            .field("traceId")
                            .value(traceId)
                    )
            );

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_PREFIX + "*")
                    .query(query)
                    .size(1000) // 最多返回1000条
                    .sort(sort -> sort.field(f -> f.field("timestamp").order(SortOrder.Asc)))
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            List<LogDTO> logs = new ArrayList<>();
            if (response.hits().hits() != null) {
                for (Hit<Map> hit : response.hits().hits()) {
                    logs.add(convertToLogDTO(hit.source()));
                }
            }

            return logs;

        } catch (Exception e) {
            log.error("Failed to query logs by traceId: {}", traceId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 构建索引模式
     */
    private String buildIndexPattern(QueryDTO queryDTO) {
        StringBuilder pattern = new StringBuilder(INDEX_PREFIX);

        // 添加租户ID
        if (queryDTO.getTenantId() != null) {
            pattern.append(queryDTO.getTenantId().toLowerCase()).append("-");
        } else {
            pattern.append("*-");
        }

        // 添加系统ID
        if (queryDTO.getSystemId() != null) {
            pattern.append(queryDTO.getSystemId().toLowerCase()).append("-");
        } else {
            pattern.append("*-");
        }

        // 添加日期范围（简化处理，使用通配符）
        pattern.append("*");

        return pattern.toString();
    }

    /**
     * 构建查询条件
     */
    private Query buildQuery(QueryDTO queryDTO) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // 时间范围
        if (queryDTO.getStartTime() != null || queryDTO.getEndTime() != null) {
            boolQuery.filter(f -> f.range(r -> {
                var rangeQuery = r.field("timestamp");
                if (queryDTO.getStartTime() != null) {
                    rangeQuery.gte(JsonData.of(queryDTO.getStartTime().toString()));
                }
                if (queryDTO.getEndTime() != null) {
                    rangeQuery.lte(JsonData.of(queryDTO.getEndTime().toString()));
                }
                return rangeQuery;
            }));
        }

        // 日志级别
        if (queryDTO.getLevel() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("level").value(queryDTO.getLevel())));
        }

        // 用户ID
        if (queryDTO.getUserId() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("userId").value(queryDTO.getUserId())));
        }

        // 用户名
        if (queryDTO.getUserName() != null) {
            boolQuery.filter(f -> f.wildcard(w -> w.field("userName").value("*" + queryDTO.getUserName() + "*")));
        }

        // 功能模块
        if (queryDTO.getModule() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("module").value(queryDTO.getModule())));
        }

        // 操作类型
        if (queryDTO.getOperation() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("operation").value(queryDTO.getOperation())));
        }

        // 关键字搜索（模糊匹配message字段）
        if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().isEmpty()) {
            boolQuery.must(m -> m.match(ma -> ma.field("message").query(queryDTO.getKeyword())));
        }

        // TraceId
        if (queryDTO.getTraceId() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("traceId").value(queryDTO.getTraceId())));
        }

        // 响应时间范围
        if (queryDTO.getMinResponseTime() != null || queryDTO.getMaxResponseTime() != null) {
            boolQuery.filter(f -> f.range(r -> {
                var rangeQuery = r.field("responseTime");
                if (queryDTO.getMinResponseTime() != null) {
                    rangeQuery.gte(JsonData.of(queryDTO.getMinResponseTime().doubleValue()));
                }
                if (queryDTO.getMaxResponseTime() != null) {
                    rangeQuery.lte(JsonData.of(queryDTO.getMaxResponseTime().doubleValue()));
                }
                return rangeQuery;
            }));
        }

        return Query.of(q -> q.bool(boolQuery.build()));
    }

    /**
     * 转换为 LogDTO
     */
    private LogDTO convertToLogDTO(Map<String, Object> source) {
        LogDTO log = new LogDTO();
        log.setTraceId((String) source.get("traceId"));
        log.setSpanId((String) source.get("spanId"));
        log.setTenantId((Long) source.get("tenantId"));
        log.setSystemId((Long) source.get("systemId"));
        log.setLevel((String) source.get("level"));
        log.setLogger((String) source.get("logger"));
        log.setThread((String) source.get("thread"));
        log.setClassName((String) source.get("className"));
        log.setMethodName((String) source.get("methodName"));
        log.setMessage((String) source.get("message"));
        log.setException((String) source.get("exception"));
        log.setUserId((String) source.get("userId"));
        log.setUserName((String) source.get("userName"));
        log.setModule((String) source.get("module"));
        log.setOperation((String) source.get("operation"));
        log.setRequestUrl((String) source.get("requestUrl"));
        log.setRequestMethod((String) source.get("requestMethod"));
        log.setRequestParams((String) source.get("requestParams"));
        log.setIp((String) source.get("ip"));
        log.setUserAgent((String) source.get("userAgent"));

        // 响应时间
        Object responseTime = source.get("responseTime");
        if (responseTime instanceof Number) {
            log.setResponseTime(((Number) responseTime).longValue());
        }

        // 行号
        Object lineNumber = source.get("lineNumber");
        if (lineNumber instanceof Number) {
            log.setLineNumber(((Number) lineNumber).intValue());
        }

        return log;
    }
}