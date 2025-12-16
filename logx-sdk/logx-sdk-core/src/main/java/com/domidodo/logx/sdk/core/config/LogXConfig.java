package com.domidodo.logx.sdk.core.config;

import lombok.Data;


import java.time.Duration;

@Data
public class LogXConfig {
    private Long tenantId;
    private Long systemId;
    private String systemName;
    private String gatewayUrl;
    private String grpcHost;
    private int grpcPort;
    private int grpcMaxInboundMessageSize = 4194304;
    private int grpcMaxOutboundMessageSize = 4194304;
    private int grpcMaxConcurrentStreams = 100;
    private int grpcMaxConcurrentCallsPerConnection = 100;
    private String mode;
    private String apiKey;

    // 缓冲配置
    private boolean bufferEnabled = true;
    private int bufferSize = 1000;
    private Duration flushInterval = Duration.ofSeconds(5);

    // HTTP 配置
    private int connectTimeout = 5000;
    private int readTimeout = 5000;
    private int maxRetries = 3;
}
