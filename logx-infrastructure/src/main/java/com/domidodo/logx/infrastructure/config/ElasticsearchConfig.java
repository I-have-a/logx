package com.domidodo.logx.infrastructure.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch 配置
 */
@Slf4j
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.domidodo.logx.*.repository")
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String[] uris;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Value("${spring.elasticsearch.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${spring.elasticsearch.socket-timeout:30000}")
    private int socketTimeout;

    /**
     * 创建 RestClient
     */
    @Bean
    public RestClient restClient() {
        HttpHost[] hosts = new HttpHost[uris.length];
        for (int i = 0; i < uris.length; i++) {
            String uri = uris[i];
            String host = uri.replace("http://", "").replace("https://", "");
            String[] parts = host.split(":");
            hosts[i] = new HttpHost(parts[0],
                    parts.length > 1 ? Integer.parseInt(parts[1]) : 9200,
                    uri.startsWith("https") ? "https" : "http");
        }

        RestClientBuilder builder = RestClient.builder(hosts)
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(connectionTimeout)
                        .setSocketTimeout(socketTimeout));

        // 如果配置了用户名密码，添加认证
        if (username != null && !username.isEmpty()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));

            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        RestClient restClient = builder.build();
        log.info("Elasticsearch RestClient已用主机初始化：{}", (Object) uris);
        return restClient;
    }

    /**
     * 创建 ElasticsearchClient（新版API）
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {

        JacksonJsonpMapper jacksonJsonpMapper = new JacksonJsonpMapper();
        ObjectMapper objectMapper = jacksonJsonpMapper.objectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 使用配置好的映射器创建传输层
        ElasticsearchTransport transport = new RestClientTransport(
                restClient,
                jacksonJsonpMapper
        );
        ElasticsearchClient client = new ElasticsearchClient(transport);
        log.info("ElasticsearchClient已初始化");
        return client;
    }
}