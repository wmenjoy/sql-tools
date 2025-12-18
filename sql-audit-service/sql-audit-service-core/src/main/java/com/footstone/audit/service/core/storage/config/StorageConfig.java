package com.footstone.audit.service.core.storage.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;
import java.util.Properties;

@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "audit.storage.mode", havingValue = "full", matchIfMissing = true)
    public ClickHouseDataSource clickHouseDataSource(
            @Value("${audit.storage.clickhouse.url}") String url,
            @Value("${audit.storage.clickhouse.username:default}") String username,
            @Value("${audit.storage.clickhouse.password:}") String password
    ) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        return new ClickHouseDataSource(url, properties);
    }
}
