package com.footstone.audit.service.web.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for ClickHouse connectivity.
 * Reports ClickHouse database connection status.
 */
@Component
public class ClickHouseHealthIndicator implements HealthIndicator {
    
    // In a real implementation, this would use a ClickHouse connection
    private volatile boolean connected = true;
    private volatile String version = "24.1";
    private volatile long lastQueryTime = System.currentTimeMillis();
    private volatile String lastError = null;
    
    @Override
    public Health health() {
        if (!connected) {
            return Health.down()
                .withDetail("error", lastError != null ? lastError : "Connection failed")
                .withDetail("lastQueryTime", lastQueryTime)
                .build();
        }
        
        return Health.up()
            .withDetail("status", "connected")
            .withDetail("version", version)
            .withDetail("lastQueryTime", lastQueryTime)
            .build();
    }
    
    // Methods for updating status
    public void setConnected(boolean connected) {
        this.connected = connected;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public void setLastError(String error) {
        this.lastError = error;
    }
    
    public void recordQuery() {
        this.lastQueryTime = System.currentTimeMillis();
        this.connected = true;
        this.lastError = null;
    }
}








