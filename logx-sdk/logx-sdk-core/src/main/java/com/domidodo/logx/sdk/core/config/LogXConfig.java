package com.domidodo.logx.sdk.core.config;

import lombok.Data;
import java.time.Duration;

/**
 * LogX 配置类
 * 类型统一使用 String，与 gRPC Proto 保持一致
 */
@Data
public class LogXConfig {
    // ============ 租户信息 ============
    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 系统ID
     */
    private String systemId;

    /**
     * 系统名称
     */
    private String systemName;

    // ============ 认证信息 ============
    /**
     * API 密钥
     */
    private String apiKey;

    // ============ 网关配置 ============
    /**
     * 通信模式：http 或 grpc
     */
    private String mode = "http";

    /**
     * HTTP 网关地址
     */
    private String gatewayUrl;

    /**
     * gRPC 主机
     */
    private String grpcHost;

    /**
     * gRPC 端口
     */
    private int grpcPort = 9090;

    /**
     * gRPC 模式：批量传输模式（batch | stream）
     */
    private String batchMode = "stream";

    // ============ gRPC 高级配置 ============
    /**
     * gRPC 最大入站消息大小（字节）
     */
    private int grpcMaxInboundMessageSize = 10 * 1024 * 1024; // 10MB

    /**
     * gRPC 最大出站消息大小（字节）
     */
    private int grpcMaxOutboundMessageSize = 10 * 1024 * 1024; // 10MB

    /**
     * gRPC 最大并发流数量
     */
    private int grpcMaxConcurrentStreams = 100;

    /**
     * gRPC 每个连接的最大并发调用数
     */
    private int grpcMaxConcurrentCallsPerConnection = 100;

    // ============ 缓冲配置 ============
    /**
     * 是否启用缓冲
     */
    private boolean bufferEnabled = true;

    /**
     * 缓冲区大小
     */
    private int bufferSize = 1000;

    /**
     * 刷新间隔
     */
    private Duration flushInterval = Duration.ofSeconds(5);

    // ============ HTTP 配置 ============
    /**
     * 连接超时（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeout = 5000;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;
}