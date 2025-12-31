package com.domidodo.logx.engine.storage.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.domidodo.logx.engine.storage.elasticsearch.EsDataExporter;
import com.domidodo.logx.engine.storage.model.LogExcelDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Excel 导出服务
 * 使用 EasyExcel 实现日志数据导出为 Excel 文件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final EsDataExporter esDataExporter;

    /**
     * Excel 单 Sheet 最大行数（Excel 2007+ 限制为 1048576）
     * 为了性能考虑，设置为 100 万
     */
    private static final int MAX_ROWS_PER_SHEET = 1000000;

    /**
     * 每批次处理的数据量
     */
    private static final int BATCH_SIZE = 5000;

    /**
     * 导出索引数据到 Excel（流式输出）
     *
     * @param indexName    索引名称
     * @param outputStream 输出流
     * @return 导出的记录数
     */
    public long exportToExcel(String indexName, OutputStream outputStream) {
        log.info("开始导出 Excel: {}", indexName);
        long startTime = System.currentTimeMillis();
        AtomicLong totalCount = new AtomicLong(0);

        // 获取总数用于日志
        long estimatedTotal = esDataExporter.getIndexDocumentCount(indexName);
        log.info("预计导出记录数: {}", estimatedTotal);

        try (ExcelWriter excelWriter = EasyExcel.write(outputStream, LogExcelDTO.class)
                .registerWriteHandler(createCellStyleStrategy())
                .build()) {

            WriteSheet writeSheet = EasyExcel.writerSheet("日志数据").build();
            List<LogExcelDTO> batch = new ArrayList<>(BATCH_SIZE);

            esDataExporter.exportIndexWithBatchProcessor(indexName, documents -> {
                for (Map<String, Object> document : documents) {
                    batch.add(LogExcelDTO.fromMap(document));
                    totalCount.incrementAndGet();

                    // 批量写入
                    if (batch.size() >= BATCH_SIZE) {
                        excelWriter.write(batch, writeSheet);
                        batch.clear();

                        // 记录进度
                        if (totalCount.get() % 50000 == 0) {
                            log.info("Excel 导出进度: {}/{} ({}%)",
                                    totalCount.get(), estimatedTotal,
                                    String.format("%.1f", (double) totalCount.get() / estimatedTotal * 100));
                        }
                    }
                }
            });

            // 写入剩余数据
            if (!batch.isEmpty()) {
                excelWriter.write(batch, writeSheet);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Excel 导出完成: {}, 记录数: {}, 耗时: {}ms",
                    indexName, totalCount.get(), duration);

        } catch (Exception e) {
            log.error("Excel 导出失败: {}", indexName, e);
            throw new RuntimeException("Excel 导出失败: " + e.getMessage(), e);
        }

        return totalCount.get();
    }

    /**
     * 导出索引数据到 Excel 文件
     *
     * @param indexName 索引名称
     * @param filePath  文件路径
     * @return 导出的记录数
     */
    public long exportToExcelFile(String indexName, String filePath) {
        log.info("开始导出 Excel 文件: {} -> {}", indexName, filePath);
        long startTime = System.currentTimeMillis();
        AtomicLong totalCount = new AtomicLong(0);

        try (ExcelWriter excelWriter = EasyExcel.write(filePath, LogExcelDTO.class)
                .registerWriteHandler(createCellStyleStrategy())
                .build()) {

            WriteSheet writeSheet = EasyExcel.writerSheet("日志数据").build();
            List<LogExcelDTO> batch = new ArrayList<>(BATCH_SIZE);

            esDataExporter.exportIndexWithBatchProcessor(indexName, documents -> {
                for (Map<String, Object> document : documents) {
                    batch.add(LogExcelDTO.fromMap(document));
                    totalCount.incrementAndGet();

                    if (batch.size() >= BATCH_SIZE) {
                        excelWriter.write(batch, writeSheet);
                        batch.clear();
                    }
                }
            });

            if (!batch.isEmpty()) {
                excelWriter.write(batch, writeSheet);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Excel 文件导出完成: {}, 记录数: {}, 耗时: {}ms, 文件: {}",
                    indexName, totalCount.get(), duration, filePath);

        } catch (Exception e) {
            log.error("Excel 文件导出失败: {}", indexName, e);
            throw new RuntimeException("Excel 文件导出失败: " + e.getMessage(), e);
        }

        return totalCount.get();
    }

    /**
     * 导出大数据量到多个 Sheet
     * 每个 Sheet 最多 100 万行
     *
     * @param indexName    索引名称
     * @param outputStream 输出流
     * @return 导出的记录数
     */
    public long exportToExcelMultiSheet(String indexName, OutputStream outputStream) {
        log.info("开始多 Sheet 导出 Excel: {}", indexName);
        long startTime = System.currentTimeMillis();
        AtomicLong totalCount = new AtomicLong(0);
        AtomicLong sheetRowCount = new AtomicLong(0);
        final int[] sheetIndex = {0};

        try (ExcelWriter excelWriter = EasyExcel.write(outputStream, LogExcelDTO.class)
                .registerWriteHandler(createCellStyleStrategy())
                .build()) {

            WriteSheet[] currentSheet = {EasyExcel.writerSheet(sheetIndex[0], "日志数据-1").build()};
            List<LogExcelDTO> batch = new ArrayList<>(BATCH_SIZE);

            esDataExporter.exportIndexWithBatchProcessor(indexName, documents -> {
                for (Map<String, Object> document : documents) {
                    batch.add(LogExcelDTO.fromMap(document));
                    totalCount.incrementAndGet();
                    sheetRowCount.incrementAndGet();

                    // 检查是否需要新 Sheet
                    if (sheetRowCount.get() >= MAX_ROWS_PER_SHEET) {
                        // 先写入当前批次
                        if (!batch.isEmpty()) {
                            excelWriter.write(batch, currentSheet[0]);
                            batch.clear();
                        }

                        // 创建新 Sheet
                        sheetIndex[0]++;
                        currentSheet[0] = EasyExcel.writerSheet(sheetIndex[0],
                                "日志数据-" + (sheetIndex[0] + 1)).build();
                        sheetRowCount.set(0);
                        log.info("创建新 Sheet: 日志数据-{}", sheetIndex[0] + 1);
                    }

                    // 批量写入
                    if (batch.size() >= BATCH_SIZE) {
                        excelWriter.write(batch, currentSheet[0]);
                        batch.clear();
                    }
                }
            });

            // 写入剩余数据
            if (!batch.isEmpty()) {
                excelWriter.write(batch, currentSheet[0]);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("多 Sheet Excel 导出完成: {}, 记录数: {}, Sheet数: {}, 耗时: {}ms",
                    indexName, totalCount.get(), sheetIndex[0] + 1, duration);

        } catch (Exception e) {
            log.error("多 Sheet Excel 导出失败: {}", indexName, e);
            throw new RuntimeException("Excel 导出失败: " + e.getMessage(), e);
        }

        return totalCount.get();
    }

    /**
     * 导出指定日期范围的日志
     *
     * @param tenantId     租户ID
     * @param systemId     系统ID
     * @param startDate    开始日期
     * @param endDate      结束日期
     * @param outputStream 输出流
     * @return 导出的记录数
     */
    public long exportByDateRange(String tenantId, String systemId,
                                  LocalDate startDate, LocalDate endDate,
                                  OutputStream outputStream) {
        log.info("按日期范围导出 Excel: tenant={}, system={}, {} ~ {}",
                tenantId, systemId, startDate, endDate);

        long totalCount = 0;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");

        try (ExcelWriter excelWriter = EasyExcel.write(outputStream, LogExcelDTO.class)
                .registerWriteHandler(createCellStyleStrategy())
                .build()) {

            int sheetIndex = 0;

            // 遍历日期范围
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                String indexName = String.format("logx-logs-%s-%s-%s",
                        tenantId, systemId, date.format(formatter));

                // 检查索引是否存在
                long docCount = esDataExporter.getIndexDocumentCount(indexName);
                if (docCount == 0) {
                    log.debug("索引无数据或不存在: {}", indexName);
                    continue;
                }

                String sheetName = date.format(DateTimeFormatter.ofPattern("MM-dd"));
                WriteSheet writeSheet = EasyExcel.writerSheet(sheetIndex++, sheetName).build();
                List<LogExcelDTO> batch = new ArrayList<>(BATCH_SIZE);

                AtomicLong indexCount = new AtomicLong(0);
                esDataExporter.exportIndexWithBatchProcessor(indexName, documents -> {
                    for (Map<String, Object> document : documents) {
                        batch.add(LogExcelDTO.fromMap(document));
                        indexCount.incrementAndGet();

                        if (batch.size() >= BATCH_SIZE) {
                            excelWriter.write(batch, writeSheet);
                            batch.clear();
                        }
                    }
                });

                if (!batch.isEmpty()) {
                    excelWriter.write(batch, writeSheet);
                }

                totalCount += indexCount.get();
                log.info("日期 {} 导出完成: {} 条", date, indexCount.get());
            }

            log.info("日期范围导出完成: {} ~ {}, 总记录数: {}", startDate, endDate, totalCount);

        } catch (Exception e) {
            log.error("日期范围导出失败", e);
            throw new RuntimeException("Excel 导出失败: " + e.getMessage(), e);
        }

        return totalCount;
    }

    /**
     * 生成临时 Excel 文件
     *
     * @param indexName 索引名称
     * @return 临时文件
     */
    public File exportToTempFile(String indexName) {
        String tempDir = System.getProperty("java.io.tmpdir");
        String fileName = String.format("logx-export-%s-%d.xlsx",
                indexName.replaceAll("[^a-zA-Z0-9-]", "_"),
                System.currentTimeMillis());

        File tempFile = new File(tempDir, fileName);
        exportToExcelFile(indexName, tempFile.getAbsolutePath());

        log.info("生成临时 Excel 文件: {}", tempFile.getAbsolutePath());
        return tempFile;
    }

    /**
     * 创建单元格样式策略
     */
    private HorizontalCellStyleStrategy createCellStyleStrategy() {
        // 头部样式
        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headStyle.setBorderTop(BorderStyle.THIN);
        headStyle.setBorderBottom(BorderStyle.THIN);
        headStyle.setBorderLeft(BorderStyle.THIN);
        headStyle.setBorderRight(BorderStyle.THIN);

        WriteFont headFont = new WriteFont();
        headFont.setFontName("微软雅黑");
        headFont.setFontHeightInPoints((short) 11);
        headFont.setBold(true);
        headStyle.setWriteFont(headFont);

        // 内容样式
        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        contentStyle.setBorderTop(BorderStyle.THIN);
        contentStyle.setBorderBottom(BorderStyle.THIN);
        contentStyle.setBorderLeft(BorderStyle.THIN);
        contentStyle.setBorderRight(BorderStyle.THIN);
        contentStyle.setWrapped(true); // 自动换行

        WriteFont contentFont = new WriteFont();
        contentFont.setFontName("微软雅黑");
        contentFont.setFontHeightInPoints((short) 10);
        contentStyle.setWriteFont(contentFont);

        return new HorizontalCellStyleStrategy(headStyle, contentStyle);
    }

    /**
     * 估算导出文件大小
     *
     * @param indexName 索引名称
     * @return 预估大小（字节）
     */
    public long estimateExcelSize(String indexName) {
        long documentCount = esDataExporter.getIndexDocumentCount(indexName);
        // Excel 每行约 1KB（含格式信息）
        return documentCount * 1024;
    }

    /**
     * 检查是否需要分文件导出
     *
     * @param indexName 索引名称
     * @return 是否需要分文件
     */
    public boolean needsMultiFileExport(String indexName) {
        long documentCount = esDataExporter.getIndexDocumentCount(indexName);
        // 超过 100 万行需要分文件
        return documentCount > MAX_ROWS_PER_SHEET;
    }
}