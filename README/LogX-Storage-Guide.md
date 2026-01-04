# LogX Storage 模块技术文档

## 📑 目录

- [模块概述](#模块概述)
- [数据生命周期管理](#数据生命周期管理)
- [ES模板管理](#es模板管理)
- [数据导出归档](#数据导出归档)
- [定时任务](#定时任务)
- [配置指南](#配置指南)

---

## 模块概述

### 核心功能

```
Storage模块负责：
├── 索引模板管理      # EsTemplateManager
├── 数据生命周期      # HotColdStrategy + DataCleanupJob
├── 数据导出归档      # EsDataExporter + ChunkedDataExporter
├── 批量并发导出      # BatchExportService
└── MinIO对象存储     # MinioStorageService
```

### 数据流转路径

```
热数据(ES) → 温数据(ES只读) → 冷数据(MinIO归档) → 删除
  7天           30天              90天          清理
```

---

## 数据生命周期管理

### 1. 热冷策略 (HotColdStrategy)

#### 数据层级定义

```java
public enum DataTier {
    HOT,    // 热数据 - Elasticsearch，可读写
    WARM,   // 温数据 - Elasticsearch，只读
    COLD,   // 冷数据 - MinIO，归档
    DELETED // 已删除
}
```

#### 层级判断逻辑

```java
public DataTier determineDataTier(LocalDate date) {
    LocalDate now = LocalDate.now();
    long daysDiff = now.toEpochDay() - date.toEpochDay();
    
    if (daysDiff <= hotDataDays) {        // 默认 7天
        return DataTier.HOT;
    } else if (daysDiff <= warmDataDays) {  // 默认 30天
        return DataTier.WARM;
    } else if (daysDiff <= coldDataDays) {  // 默认 90天
        return DataTier.COLD;
    } else {
        return DataTier.DELETED;
    }
}
```

**配置示例**:
```yaml
logx:
  storage:
    lifecycle:
      hot-data-days: 7      # 热数据保留7天
      warm-data-days: 30    # 温数据保留30天
      cold-data-days: 90    # 冷数据保留90天
      archive-enabled: true
      cleanup-enabled: true
```

#### 迁移优先级

```java
private int calculatePriority(LocalDate date) {
    long daysDiff = now.toEpochDay() - date.toEpochDay();
    
    if (daysDiff > coldDataDays + 7) {
        return 10; // 超期1周，最高优先级（立即删除）
    } else if (daysDiff > coldDataDays) {
        return 8;  // 超期但不到1周（尽快归档）
    } else if (daysDiff > warmDataDays + 3) {
        return 6;  // 即将进入冷数据（准备归档）
    } else if (daysDiff > warmDataDays) {
        return 4;  // 刚进入温数据（设置只读）
    } else if (daysDiff > hotDataDays + 1) {
        return 2;  // 即将进入温数据
    } else {
        return 1;  // 热数据，低优先级
    }
}
```

#### 存储成本估算

```java
public double estimateMonthlyCost(long dataSize, DataTier tier) {
    double costFactor = switch (tier) {
        case HOT -> 1.0;    // 高性能，高成本
        case WARM -> 0.5;   // 标准性能，中等成本
        case COLD -> 0.1;   // 归档，低成本
        case DELETED -> 0.0;
    };
    
    double sizeInGB = dataSize / (1024.0 * 1024.0 * 1024.0);
    return sizeInGB * costFactor; // 假设每GB每月成本为1个单位
}
```

**成本对比**:

| 层级 | 性能 | 成本系数 | 100GB月成本 |
|------|------|---------|------------|
| HOT | 高 | 1.0 | 100单位 |
| WARM | 中 | 0.5 | 50单位 |
| COLD | 低 | 0.1 | 10单位 |

---

### 2. 索引模式匹配 (IndexPatternMatcher)

#### 索引命名规则

```
格式: {prefix}-{tenantId}-{systemId}-{yyyy.MM.dd}
示例: logx-logs-company_a-erp_system-2024.12.27
```

**代码实现**:
```java
public boolean matchesPattern(String indexName) {
    return indexName.startsWith(storageConfig.getIndex().getPrefix() + "-");
}

public LocalDate extractDate(String indexName) {
    String[] parts = indexName.split("-");
    if (parts.length >= 5) {
        String datePart = parts[4];  // 格式：yyyy.MM.dd
        return LocalDate.parse(datePart, indexDateFormatter);
    }
    return null;
}
```

---

### 3. 定时清理任务 (DataCleanupJob)

#### 任务调度

```java
/**
 * 生命周期管理 - 每天凌晨2点
 */
@Scheduled(cron = "${logx.storage.lifecycle.cleanup-cron:0 0 2 * * ?}")
public void executeLifecycleManagement() {
    lifecycleManager.executeLifecycleManagement();
}

/**
 * 归档任务 - 每天凌晨3点
 */
@Scheduled(cron = "${logx.storage.lifecycle.archive-cron:0 0 3 * * ?}")
public void executeArchiveTask() {
    // 归档任务
}

/**
 * 存储统计 - 每小时
 */
@Scheduled(cron = "0 0 * * * ?")
public void generateStorageStats() {
    var stats = lifecycleManager.getStorageStats();
    log.info("存储统计信息: {}", stats);
}
```

**配置示例**:
```yaml
logx:
  storage:
    lifecycle:
      cleanup-cron: "0 0 2 * * ?"   # 每天凌晨2点清理
      archive-cron: "0 0 3 * * ?"   # 每天凌晨3点归档
      cleanup-enabled: true
      archive-enabled: true
```

---

## ES模板管理

### 1. 模板管理器 (EsTemplateManager)

#### 初始化流程

```java
@PostConstruct
public void initIndexTemplate() {
    // 1. 创建索引模板
    createLogIndexTemplate();
    
    // 2. 创建生命周期策略（需要X-Pack）
    try {
        createLifecyclePolicy();
    } catch (Exception e) {
        log.warn("生命周期策略创建失败（可能不支持该功能）");
    }
}
```

#### 模板设置

```java
private IndexSettings buildTemplateSettings() {
    return IndexSettings.of(s -> s
        .numberOfShards(String.valueOf(storageConfig.getIndex().getShards()))     // 5个分片
        .numberOfReplicas(String.valueOf(storageConfig.getIndex().getReplicas())) // 1个副本
        .refreshInterval(time -> time.time("5s"))                                  // 5秒刷新
        .codec("best_compression")                                                 // 最佳压缩
        .maxResultWindow(10000)                                                    // 最大返回10000条
    );
}
```

#### 字段映射（完整版）

**25个字段定义**:

| 类别 | 字段 | ES类型 | 说明 |
|------|------|--------|------|
| **追踪** | traceId | keyword | 分布式追踪ID |
| | spanId | keyword | 调用链ID |
| **租户** | tenantId | keyword | 租户ID |
| | systemId | keyword | 系统ID |
| **时间** | timestamp | date | 时间戳（支持毫秒） |
| **日志** | level | keyword | 日志级别 |
| | logger | keyword | Logger名称 |
| | thread | keyword | 线程名 |
| **代码** | className | keyword | 类名 |
| | methodName | keyword | 方法名 |
| | lineNumber | integer | 行号 |
| **内容** | message | text | 日志消息（支持分词） |
| | exception | text | 异常堆栈 |
| **用户** | userId | keyword | 用户ID |
| | userName | keyword | 用户名 |
| **业务** | module | keyword | 功能模块 |
| | operation | keyword | 操作类型 |
| **请求** | requestUrl | keyword | 请求URL |
| | requestMethod | keyword | 请求方法 |
| | requestParams | text | 请求参数 |
| | responseTime | long | 响应时间(ms) |
| **网络** | ip | ip | 客户端IP |
| | userAgent | text | User-Agent |
| **扩展** | tags | keyword | 标签数组 |
| | extra | object | 扩展字段（不索引） |

**关键字段配置**:

```java
// 1. 支持中文分词的消息字段
properties.put("message", Property.of(p -> p.text(t -> t
    .analyzer("ik_max_word")  // ik分词器
    .fields("keyword", Property.of(kf -> kf.keyword(k -> k.ignoreAbove(256))))
)));

// 2. IP类型字段
properties.put("ip", Property.of(p -> p.ip(i -> i)));

// 3. 时间戳字段（支持多种格式）
properties.put("timestamp", Property.of(p -> p.date(d -> d
    .format("strict_date_optional_time||epoch_millis")
)));

// 4. 扩展字段（不索引，仅存储）
properties.put("extra", Property.of(p -> p.object(o -> o.enabled(false))));
```

#### 模板配置

```java
PutIndexTemplateRequest request = PutIndexTemplateRequest.of(t -> t
    .name("logx-logs-template")
    .indexPatterns("logx-logs-*")
    .template(template -> template
        .settings(buildTemplateSettings())
        .mappings(buildTemplateMappings())
        .aliases("logx-logs", a -> a)
    )
    .priority(200)  // 优先级
);
```

---

## 数据导出归档

### 1. ES数据导出器 (EsDataExporter)

#### Scroll API 导出

**特点**:
- 使用Scroll API分批查询
- 支持大数据量（百万级）
- 自动清理Scroll上下文

**代码实现**:
```java
private void scrollQuery(String indexName, Consumer<Map<String, Object>> documentConsumer) {
    String scrollId = null;
    
    try {
        // 初始化Scroll查询
        SearchResponse<Map> response = elasticsearchClient.search(s -> s
            .index(indexName)
            .size(500)  // 每批500条
            .scroll(Time.of(t -> t.time("5m")))  // 5分钟超时
            .query(q -> q.matchAll(m -> m)),
            Map.class
        );
        
        scrollId = response.scrollId();
        List<Hit<Map>> hits = response.hits().hits();
        
        // 处理第一批
        processHits(hits, documentConsumer);
        
        // 继续滚动
        while (hits != null && !hits.isEmpty()) {
            ScrollResponse<Map> scrollResponse = elasticsearchClient.scroll(s -> s
                .scrollId(scrollId)
                .scroll(Time.of(t -> t.time("5m"))),
                Map.class
            );
            
            scrollId = scrollResponse.scrollId();
            hits = scrollResponse.hits().hits();
            
            if (hits != null && !hits.isEmpty()) {
                processHits(hits, documentConsumer);
            }
        }
        
    } finally {
        // 确保清理Scroll上下文
        if (scrollId != null) {
            clearScroll(scrollId);
        }
    }
}
```

#### 导出方法

**1. 完整导出（小数据量）**:
```java
// ⚠️ 会将所有数据加载到内存
String json = esDataExporter.exportIndexToJson("logx-logs-xxx");

// 适用场景：< 10万条记录
```

**2. 流式导出（推荐）**:
```java
// 批量处理，避免内存溢出
long totalCount = esDataExporter.exportIndexWithBatchProcessor(
    indexName,
    batch -> {
        // 处理每批数据（500条）
        saveToDisk(batch);
    }
);

// 适用场景：任意数据量
```

**3. 带进度监控**:
```java
String json = esDataExporter.exportIndexWithProgress(
    indexName,
    progress -> {
        System.out.printf("进度: %.2f%% (%d/%d)\n",
            progress.getProgress(),
            progress.getProcessedCount(),
            progress.getTotalDocuments()
        );
    }
);
```

---

### 2. 分块导出器 (ChunkedDataExporter)

#### 适用场景

| 文档数 | 预估大小 | 推荐方法 |
|--------|---------|---------|
| < 10万 | < 200MB | exportIndexToJson |
| 10-50万 | 200MB-1GB | exportIndexWithBatchProcessor |
| > 50万 | > 1GB | exportAndArchiveInChunks |

**自动判断**:
```java
public boolean needsChunkedExport(String indexName) {
    long documentCount = esDataExporter.getIndexDocumentCount(indexName);
    long estimatedSize = estimateExportSize(indexName);
    
    // 超过50万条或预估大小超过1GB，使用分块导出
    return documentCount > 500000 || estimatedSize > 1024 * 1024 * 1024;
}
```

#### 分块导出到MinIO

```java
public boolean exportAndArchiveInChunks(String indexName, String tenantId,
                                        String systemId, LocalDate date) {
    // 创建临时文件
    File tempFile = createTempFile(indexName);
    
    try (BufferedWriter writer = new BufferedWriter(...)) {
        AtomicLong totalCount = new AtomicLong(0);
        
        // 写入数组开始
        writer.write("[");
        
        // 流式处理
        esDataExporter.exportIndexWithBatchProcessor(indexName, batch -> {
            for (Map<String, Object> document : batch) {
                if (totalCount.get() > 0) {
                    writer.write(",");
                }
                writer.write(JSON.toJSONString(document));
                totalCount.incrementAndGet();
                
                // 每10000条刷新一次
                if (totalCount.get() % 10000 == 0) {
                    writer.flush();
                    log.info("已处理 {} 条文档", totalCount.get());
                }
            }
        });
        
        // 写入数组结束
        writer.write("]");
    }
    
    // 上传到MinIO
    String jsonData = IoUtil.read(new FileInputStream(tempFile));
    minioStorageService.archiveLogs(tenantId, systemId, date, jsonData);
    
    // 删除临时文件
    tempFile.delete();
    
    return true;
}
```

---

### 3. 批量并发导出 (BatchExportService)

#### 线程池配置

```java
private static final int CORE_POOL_SIZE = 2;
private static final int MAX_POOL_SIZE = 5;
private static final int QUEUE_CAPACITY = 100;

@PostConstruct
public void init() {
    executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(CORE_POOL_SIZE);
    executor.setMaxPoolSize(MAX_POOL_SIZE);
    executor.setQueueCapacity(QUEUE_CAPACITY);
    executor.setThreadNamePrefix("export-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
}
```

#### 批量导出

```java
public BatchExportResult batchExport(List<ExportTask> exportTasks) {
    List<CompletableFuture<ExportTaskResult>> futures = new ArrayList<>();
    
    // 提交所有任务
    for (ExportTask task : exportTasks) {
        CompletableFuture<ExportTaskResult> future = 
            CompletableFuture.supplyAsync(() -> executeExportTask(task), executor);
        futures.add(future);
    }
    
    // 等待所有任务完成（最多30分钟）
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .get(30, TimeUnit.MINUTES);
    
    // 收集结果
    BatchExportResult result = new BatchExportResult();
    result.setTotalTasks(exportTasks.size());
    // ... 统计成功/失败数
    
    return result;
}
```

**使用示例**:
```java
// 准备导出任务
List<ExportTask> tasks = new ArrayList<>();
for (String indexName : indexNames) {
    ExportTask task = new ExportTask();
    task.setIndexName(indexName);
    task.setTenantId(tenantId);
    task.setSystemId(systemId);
    task.setDate(extractDate(indexName));
    tasks.add(task);
}

// 批量导出
BatchExportResult result = batchExportService.batchExport(tasks);

System.out.printf("总任务: %d, 成功: %d, 失败: %d, 成功率: %.2f%%\n",
    result.getTotalTasks(),
    result.getSuccessCount(),
    result.getFailureCount(),
    result.getSuccessRate()
);
```

---

## 配置指南

### 完整配置示例

```yaml
logx:
  storage:
    # 索引配置
    index:
      prefix: logx-logs
      shards: 5
      replicas: 1
      refresh-interval: 5s
    
    # 压缩配置
    compression:
      enabled: true
      codec: best_compression
    
    # 批量操作
    bulk:
      size: 500
      flush-interval: 5m
      concurrent-requests: 2
    
    # 生命周期管理
    lifecycle:
      hot-data-days: 7      # 热数据7天
      warm-data-days: 30    # 温数据30天
      cold-data-days: 90    # 冷数据90天
      cleanup-enabled: true
      archive-enabled: true
      cleanup-cron: "0 0 2 * * ?"
      archive-cron: "0 0 3 * * ?"

# Elasticsearch配置
spring:
  data:
    elasticsearch:
      uris: http://localhost:9200
      username: elastic
      password: your-password
      connection-timeout: 10000
      socket-timeout: 30000
```

---

## 最佳实践

### 1. 索引管理

**命名规范**:
```
{prefix}-{tenantId}-{systemId}-{date}
✅ logx-logs-company_a-erp_system-2024.12.27
❌ logs_20241227
```

**分片规划**:
```
日志量 < 10GB/天  → 3个分片
日志量 10-50GB/天 → 5个分片
日志量 > 50GB/天  → 10个分片
```

### 2. 生命周期配置

**推荐配置**（中小型企业）:
```yaml
hot-data-days: 7    # 最近7天高频查询
warm-data-days: 30  # 30天内偶尔查询
cold-data-days: 90  # 90天归档备份
```

**推荐配置**（大型企业）:
```yaml
hot-data-days: 3    # 最近3天高频查询
warm-data-days: 14  # 14天内偶尔查询
cold-data-days: 30  # 30天归档备份
```

### 3. 导出优化

**选择合适的导出方法**:
```java
// 小数据量 (< 10万条)
String json = esDataExporter.exportIndexToJson(indexName);

// 中等数据量 (10-50万条)
esDataExporter.exportIndexWithBatchProcessor(indexName, batch -> {
    // 批量处理
});

// 大数据量 (> 50万条)
chunkedDataExporter.exportAndArchiveInChunks(
    indexName, tenantId, systemId, date
);
```

### 4. 性能调优

**ES查询优化**:
```java
// 1. 使用Scroll API（大数据量）
.scroll(Time.of(t -> t.time("5m")))

// 2. 合理的批量大小
.size(500)  // 推荐500-1000

// 3. 只查询需要的字段
.source(s -> s.filter(f -> f.includes("field1", "field2")))
```

**导出性能**:
```
单线程导出: 约 10000条/秒
5线程并发: 约 40000条/秒

1百万条日志 ≈ 100秒（单线程）≈ 25秒（5线程）
```

---

## 监控指标

### 存储统计

```java
public Map<String, Object> getStorageStats() {
    return Map.of(
        "totalIndices", getTotalIndices(),
        "hotIndices", getHotIndices().size(),
        "warmIndices", getWarmIndices().size(),
        "coldIndices", getColdIndices().size(),
        "totalSize", getTotalSize(),
        "estimatedCost", estimateTotalCost()
    );
}
```

### 关键指标

| 指标 | 说明 | 告警阈值 |
|------|------|---------|
| 索引总数 | ES中的索引数量 | > 1000 |
| 热数据大小 | 热数据总大小 | > 500GB |
| 导出队列 | 待导出任务数 | > 100 |
| 导出失败率 | 失败任务比例 | > 5% |

---

## 故障排查

### 1. 导出失败

**现象**: 导出任务失败

**排查**:
```bash
# 1. 检查ES连接
curl http://localhost:9200/_cluster/health

# 2. 检查索引存在
curl http://localhost:9200/logx-logs-*/_count

# 3. 查看导出日志
tail -f logs/storage.log | grep "export"
```

**解决**:
```yaml
# 增加超时时间
spring:
  data:
    elasticsearch:
      socket-timeout: 60000  # 60秒
```

### 2. 内存溢出

**现象**: `OutOfMemoryError`

**排查**:
```bash
jmap -heap <pid>
```

**解决**:
```java
// 使用流式导出，不要一次性加载所有数据
esDataExporter.exportIndexWithBatchProcessor(indexName, batch -> {
    // 处理后立即释放
});
```

### 3. MinIO上传失败

**现象**: 归档到MinIO失败

**排查**:
```bash
# 检查MinIO服务
curl http://localhost:9000/minio/health/live

# 检查Bucket
mc ls minio/logx-archives
```

---

## 下一步

- 查看 [Detection模块文档](./LogX-Detection-Guide.md) 了解监控告警
- 查看 [Configuration文档](./LogX-Configuration-Guide.md) 完整配置
- 查看 [Engine文档](./LogX-Engine-Guide.md) 了解数据处理
