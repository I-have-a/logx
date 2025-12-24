package com.domidodo.logx.sdk.spring.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

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

    /**
     * 用户上下文配置
     */
    private UserContext userContext = new UserContext();

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
         * 多个来源用逗号分隔，按顺序尝试
         */
        private List<String> source = Arrays.asList("header", "session", "principal");

        // ============ 请求头配置 ============
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
         * 如果配置了此项，将使用自定义实现
         */
        private String customProviderBeanName;
    }
}