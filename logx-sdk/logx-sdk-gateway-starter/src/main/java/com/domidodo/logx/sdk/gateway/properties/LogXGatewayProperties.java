package com.domidodo.logx.sdk.gateway.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * LogX 网关配置属性
 * 
 * 专用于 Spring Cloud Gateway（WebFlux 响应式环境）
 */
@Data
@ConfigurationProperties(prefix = "logx.gateway")
public class LogXGatewayProperties {

    /**
     * 是否启用网关日志收集
     */
    private boolean enabled = true;

    /**
     * 租户ID（必填）
     */
    private String tenantId;

    /**
     * 系统ID（必填）
     */
    private String systemId;

    /**
     * 系统名称
     */
    private String systemName = "API Gateway";

    /**
     * API密钥（必填）
     */
    private String apiKey;

    /**
     * 通信模式: http 或 grpc
     */
    private String mode = "http";

    /**
     * LogX 服务端配置
     */
    private Server server = new Server();

    /**
     * 分布式追踪配置
     */
    private Trace trace = new Trace();

    /**
     * 日志配置
     */
    private Log log = new Log();

    /**
     * 缓冲配置
     */
    private Buffer buffer = new Buffer();

    @Data
    public static class Server {
        /**
         * HTTP 模式：LogX 服务端地址
         */
        private String url = "http://localhost:8080";

        /**
         * gRPC 模式：LogX 服务端主机
         */
        private String host = "localhost";

        /**
         * gRPC 模式：LogX 服务端端口
         */
        private int port = 9090;

        /**
         * gRPC 批量模式：stream 或 batch
         */
        private String batchMode = "stream";

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
    public static class Trace {
        /**
         * 是否启用分布式追踪
         */
        private boolean enabled = true;

        /**
         * TraceId 请求头名称
         */
        private String traceIdHeader = "X-Trace-Id";

        /**
         * SpanId 请求头名称
         */
        private String spanIdHeader = "X-Span-Id";

        /**
         * 是否将用户信息传递到下游服务
         */
        private boolean propagateUser = true;

        /**
         * 用户ID请求头名称
         */
        private String userIdHeader = "X-User-Id";

        /**
         * 用户名请求头名称
         */
        private String userNameHeader = "X-User-Name";

        /**
         * 租户ID请求头名称
         */
        private String tenantIdHeader = "X-Tenant-Id";
    }

    @Data
    public static class Log {
        /**
         * 是否记录请求体（注意性能影响）
         */
        private boolean logRequestBody = false;

        /**
         * 是否记录响应体（注意性能影响）
         */
        private boolean logResponseBody = false;

        /**
         * 请求体最大记录长度（字节）
         */
        private int maxRequestBodyLength = 2048;

        /**
         * 响应体最大记录长度（字节）
         */
        private int maxResponseBodyLength = 2048;

        /**
         * 排除的完整路径（不记录日志）
         */
        private List<String> excludePaths = new ArrayList<>();

        /**
         * 排除的路径前缀
         */
        private List<String> excludePathPrefixes = new ArrayList<>(List.of(
                "/actuator",
                "/health",
                "/favicon.ico"
        ));

        /**
         * 慢请求阈值（毫秒）
         */
        private long slowThreshold = 5000;

        /**
         * 是否记录请求头
         */
        private boolean logHeaders = false;

        /**
         * 需要记录的请求头列表
         */
        private List<String> includeHeaders = new ArrayList<>(List.of(
                "Content-Type",
                "User-Agent",
                "Accept",
                "Origin",
                "Referer"
        ));
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
}
