package com.domidodo.logx.sdk.spring.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LogX 配置属性
 * 类型统一使用 String，与 gRPC Proto 保持一致
 */
@Data
@ConfigurationProperties(prefix = "logx")
public class LogXProperties {

    /**
     * 是否启用 LogX
     */
    private boolean enabled = true;

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

    /**
     * API密钥（用于认证）
     */
    private String apiKey;

    /**
     * 通信模式: http 或 grpc
     */
    private String mode = "http";

    /**
     * 网关配置
     */
    private Gateway gateway = new Gateway();

    /**
     * 缓冲配置
     */
    private Buffer buffer = new Buffer();

    /**
     * AOP 自动日志收集
     */
    private Aspect aspect = new Aspect();

    @Data
    public static class Gateway {
        /**
         * HTTP 模式：网关地址
         */
        private String url = "http://localhost:8080";

        /**
         * gRPC 模式：主机
         */
        private String host = "localhost";

        /**
         * gRPC 模式：端口
         */
        private int port = 9090;

        /**
         * 连接超时（毫秒）
         */
        private int connectTimeout = 5000;

        /**
         * 读取超时（毫秒）
         */
        private int readTimeout = 5000;
    }

    @Data
    public static class Buffer {
        /**
         * 是否启用缓冲
         */
        private boolean enabled = true;

        /**
         * 缓冲区大小
         */
        private int size = 1000;

        /**
         * 刷新间隔
         */
        private Duration flushInterval = Duration.ofSeconds(5);
    }

    @Data
    public static class Aspect {
        /**
         * 是否启用 AOP 自动收集
         */
        private boolean enabled = true;

        /**
         * Controller 日志收集
         */
        private boolean controller = true;

        /**
         * Service 日志收集
         */
        private boolean service = false;

        /**
         * 记录请求参数
         */
        private boolean logArgs = true;

        /**
         * 记录响应结果
         */
        private boolean logResult = true;

        /**
         * 慢请求阈值（毫秒）
         */
        private long slowThreshold = 3000;
    }
}