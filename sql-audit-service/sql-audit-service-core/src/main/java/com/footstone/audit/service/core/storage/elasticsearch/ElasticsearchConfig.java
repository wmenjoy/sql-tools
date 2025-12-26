package com.footstone.audit.service.core.storage.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch client configuration for audit log storage.
 *
 * <h2>Configuration Properties</h2>
 * <pre>
 * audit:
 *   storage:
 *     mode: elasticsearch
 *     elasticsearch:
 *       hosts: localhost:9200
 *       username: elastic
 *       password: changeme
 *       ssl-enabled: false
 * </pre>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Multiple host support (comma-separated)</li>
 *   <li>Basic authentication</li>
 *   <li>Optional SSL/TLS</li>
 *   <li>Connection pooling</li>
 * </ul>
 *
 * @since 2.0.0
 */
@Configuration
@ConditionalOnProperty(name = "audit.storage.mode", havingValue = "elasticsearch")
@Slf4j
public class ElasticsearchConfig {

    @Value("${audit.storage.elasticsearch.hosts:localhost:9200}")
    private String hosts;

    @Value("${audit.storage.elasticsearch.username:}")
    private String username;

    @Value("${audit.storage.elasticsearch.password:}")
    private String password;

    @Value("${audit.storage.elasticsearch.ssl-enabled:false}")
    private boolean sslEnabled;

    @Value("${audit.storage.elasticsearch.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${audit.storage.elasticsearch.socket-timeout:60000}")
    private int socketTimeout;

    /**
     * Creates Elasticsearch client with configured settings.
     *
     * @return Configured ElasticsearchClient
     */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        log.info("Initializing Elasticsearch client for hosts: {}", hosts);

        RestClientBuilder builder = RestClient.builder(parseHosts());

        // Configure timeouts
        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(socketTimeout)
        );

        // Configure authentication if provided
        if (username != null && !username.isEmpty()) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password)
            );

            builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                    .setDefaultCredentialsProvider(credentialsProvider)
            );
        }

        RestClient restClient = builder.build();

        // Create transport with Jackson mapper
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        ElasticsearchTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper(mapper)
        );

        log.info("Elasticsearch client initialized successfully");
        return new ElasticsearchClient(transport);
    }

    /**
     * Parses comma-separated host list into HttpHost array.
     * Format: host1:port1,host2:port2
     */
    private HttpHost[] parseHosts() {
        String[] hostList = hosts.split(",");
        HttpHost[] httpHosts = new HttpHost[hostList.length];

        for (int i = 0; i < hostList.length; i++) {
            String[] parts = hostList[i].trim().split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;
            String scheme = sslEnabled ? "https" : "http";
            httpHosts[i] = new HttpHost(host, port, scheme);
        }

        return httpHosts;
    }
}


