package com.domidodo.logx.sdk.spring.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LogX 配置属性
 * <p>
 * 适用于 Spring Boot Servlet 环境（业务服务）
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
     * API密钥
     */
    private String apiKey;

    /**
     * 通信模式: http 或 grpc
     */
    private String mode = "http";

    /**
     * 网关配置（LogX 服务端）
     */
    private Gateway gateway = new Gateway();

    /**
     * 分布式追踪配置（新增）
     */
    private Trace trace = new Trace();

    /**
     * 缓冲配置
     */
    private Buffer buffer = new Buffer();

    /**
     * AOP 自动日志收集
     */
    private Aspect aspect = new Aspect();

    /**
     * 用户上下文配置
     */
    private UserContext userContext = new UserContext();

    /**
     * 模块配置
     */
    private Module module = new Module();

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
         * gRPC 模式：批量传输模式（batch | stream）
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

    /**
     * 分布式追踪配置（新增）
     */
    @Data
    public static class Trace {
        /**
         * 是否启用分布式追踪
         * 启用后会自动接收网关传递的 TraceId
         */
        private boolean enabled = true;
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
        private long slowThreshold = 5000;
    }

    @Data
    public static class UserContext {
        /**
         * 是否启用用户上下文自动获取
         */
        private boolean enabled = true;

        /**
         * 用户信息获取来源，支持多个：header, session, principal, parameter
         */
        private List<String> source = Arrays.asList("header", "session", "principal");

        // ============ 追踪 Header 配置 ============
        /**
         * TraceId 请求头名称
         */
        private String traceIdHeader = "X-Trace-Id";

        /**
         * SpanId 请求头名称
         */
        private String spanIdHeader = "X-Span-Id";

        // ============ 用户信息 Header 配置 ============
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

        // ============ Session配置 ============
        /**
         * 用户ID在Session中的键名
         */
        private String userIdSessionKey = "userId";

        /**
         * 用户名在Session中的键名
         */
        private String userNameSessionKey = "userName";

        /**
         * 租户ID在Session中的键名
         */
        private String tenantIdSessionKey = "tenantId";

        // ============ 请求参数配置 ============
        /**
         * 用户ID请求参数名称
         */
        private String userIdParameter = "userId";

        /**
         * 自定义用户上下文提供器Bean名称
         */
        private String customProviderBeanName;
    }

    /**
     * 模块配置
     */
    @Data
    public static class Module {
        /**
         * 是否启用模块映射
         */
        private boolean enabled = true;

        /**
         * 包名到模块名的映射
         */
        private Map<String, String> packageMapping = new HashMap<>();

        /**
         * 类名到模块名的映射
         */
        private Map<String, String> classMapping = new HashMap<>();

        /**
         * 默认模块名
         */
        private String defaultModule = "default";

        /**
         * 是否使用简化的类名作为模块名
         */
        private boolean useSimpleClassName = false;
    }
}
