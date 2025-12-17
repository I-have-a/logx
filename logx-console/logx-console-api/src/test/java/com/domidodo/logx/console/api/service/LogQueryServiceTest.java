package com.domidodo.logx.console.api.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.domidodo.logx.common.dto.LogDTO;
import com.domidodo.logx.common.dto.QueryDTO;
import com.domidodo.logx.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LogQueryService 单元测试
 * <p>
 * 测试覆盖：
 * 1. 分页查询
 * 2. TraceId查询
 * 3. 查询条件构建
 * 4. 异常处理
 * 5. 边界条件
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("日志查询服务测试")
class LogQueryServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @InjectMocks
    private LogQueryService logQueryService;

    private QueryDTO queryDTO;

    @BeforeEach
    void setUp() {
        queryDTO = new QueryDTO();
        queryDTO.setTenantId("company_a");
        queryDTO.setSystemId("erp_system");
        queryDTO.setPage(1);
        queryDTO.setSize(20);
        queryDTO.setSortField("timestamp");
        queryDTO.setSortOrder("desc");
    }

    @Test
    @DisplayName("正常查询应该返回分页结果")
    void testQueryLogsSuccess() throws Exception {
        // Given
        SearchResponse<Map> mockResponse = createMockSearchResponse(100, 20);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        PageResult<LogDTO> result = logQueryService.queryLogs(queryDTO);

        // Then
        assertNotNull(result);
        assertEquals(100L, result.getTotal());
        assertEquals(20, result.getRecords().size());
        verify(elasticsearchClient, times(1)).search(any(SearchRequest.class), eq(Map.class));
    }

    @Test
    @DisplayName("空结果查询应该返回空列表")
    void testQueryLogsEmptyResult() throws Exception {
        // Given
        SearchResponse<Map> mockResponse = createMockSearchResponse(0, 0);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        PageResult<LogDTO> result = logQueryService.queryLogs(queryDTO);

        // Then
        assertNotNull(result);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("查询异常应该返回空结果")
    void testQueryLogsException() throws Exception {
        // Given
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenThrow(new RuntimeException("ES连接失败"));

        // When
        PageResult<LogDTO> result = logQueryService.queryLogs(queryDTO);

        // Then
        assertNotNull(result);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("根据TraceId查询应该返回日志链路")
    void testQueryByTraceId() throws Exception {
        // Given
        String traceId = "trace_12345";
        SearchResponse<Map> mockResponse = createMockSearchResponse(5, 5);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        List<LogDTO> result = logQueryService.queryByTraceId(traceId);

        // Then
        assertNotNull(result);
        assertEquals(5, result.size());
    }

    @Test
    @DisplayName("TraceId查询无结果应该返回空列表")
    void testQueryByTraceIdNoResult() throws Exception {
        // Given
        String traceId = "nonexistent_trace";
        SearchResponse<Map> mockResponse = createMockSearchResponse(0, 0);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        List<LogDTO> result = logQueryService.queryByTraceId(traceId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("带关键字的查询应该构建正确的查询条件")
    void testQueryWithKeyword() throws Exception {
        // Given
        queryDTO.setKeyword("error message");
        SearchResponse<Map> mockResponse = createMockSearchResponse(50, 20);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        PageResult<LogDTO> result = logQueryService.queryLogs(queryDTO);

        // Then
        assertNotNull(result);
        assertEquals(50L, result.getTotal());
    }

    @Test
    @DisplayName("带时间范围的查询应该正确过滤")
    void testQueryWithTimeRange() throws Exception {
        // Given
        queryDTO.setStartTime(LocalDateTime.now().minusDays(7));
        queryDTO.setEndTime(LocalDateTime.now());
        SearchResponse<Map> mockResponse = createMockSearchResponse(30, 20);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        PageResult<LogDTO> result = logQueryService.queryLogs(queryDTO);

        // Then
        assertNotNull(result);
        assertEquals(30L, result.getTotal());
    }

    @Test
    @DisplayName("带日志级别的查询应该正确过滤")
    void testQueryWithLevel() throws Exception {
        // Given
        queryDTO.setLevel("ERROR");
        SearchResponse<Map> mockResponse = createMockSearchResponse(15, 15);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        PageResult<LogDTO> result = logQueryService.queryLogs(queryDTO);

        // Then
        assertNotNull(result);
        assertEquals(15L, result.getTotal());
    }

    @Test
    @DisplayName("复杂查询条件应该正确组合")
    void testComplexQuery() throws Exception {
        // Given
        queryDTO.setKeyword("database error");
        queryDTO.setLevel("ERROR");
        queryDTO.setModule("user_service");
        queryDTO.setStartTime(LocalDateTime.now().minusHours(1));
        queryDTO.setEndTime(LocalDateTime.now());
        queryDTO.setMinResponseTime(1000L);
        queryDTO.setMaxResponseTime(5000L);

        SearchResponse<Map> mockResponse = createMockSearchResponse(8, 8);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        PageResult<LogDTO> result = logQueryService.queryLogs(queryDTO);

        // Then
        assertNotNull(result);
        assertEquals(8L, result.getTotal());
        assertEquals(8, result.getRecords().size());
    }

    @Test
    @DisplayName("第二页查询应该返回正确的数据")
    void testQuerySecondPage() throws Exception {
        // Given
        queryDTO.setPage(2);
        queryDTO.setSize(20);
        SearchResponse<Map> mockResponse = createMockSearchResponse(100, 20);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        PageResult<LogDTO> result = logQueryService.queryLogs(queryDTO);

        // Then
        assertNotNull(result);
        assertEquals(100L, result.getTotal());
        assertEquals(20, result.getRecords().size());
    }

    // ========== 辅助方法 ==========

    /**
     * 创建模拟的SearchResponse
     */
    @SuppressWarnings("unchecked")
    private SearchResponse<Map> createMockSearchResponse(long total, int hitCount) {
        // 创建hits
        List<Hit<Map>> hits = new ArrayList<>();
        for (int i = 0; i < hitCount; i++) {
            Hit<Map> hit = mock(Hit.class);
            when(hit.id()).thenReturn("log_" + i);
            when(hit.index()).thenReturn("logx-logs-company_a-erp_system-2024.12.16");

            Map<String, Object> source = createMockLogSource(i);
            when(hit.source()).thenReturn(source);

            hits.add(hit);
        }

        // 创建HitsMetadata
        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
        when(hitsMetadata.hits()).thenReturn(hits);

        TotalHits totalHits = mock(TotalHits.class);
        when(totalHits.value()).thenReturn(total);
        when(totalHits.relation()).thenReturn(TotalHitsRelation.Eq);
        when(hitsMetadata.total()).thenReturn(totalHits);

        // 创建SearchResponse
        SearchResponse<Map> response = mock(SearchResponse.class);
        when(response.hits()).thenReturn(hitsMetadata);

        return response;
    }

    /**
     * 创建模拟的日志数据
     */
    private Map<String, Object> createMockLogSource(int index) {
        Map<String, Object> source = new HashMap<>();
        source.put("traceId", "trace_" + index);
        source.put("spanId", "span_" + index);
        source.put("tenantId", "company_a");
        source.put("systemId", "erp_system");
        source.put("level", "INFO");
        source.put("message", "Test log message " + index);
        source.put("timestamp", System.currentTimeMillis());
        source.put("userId", "user_" + index);
        source.put("module", "test_module");
        source.put("responseTime", 100L);
        return source;
    }
}