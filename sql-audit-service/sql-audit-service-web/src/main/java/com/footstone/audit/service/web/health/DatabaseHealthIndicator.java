package com.footstone.audit.service.web.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for primary database (PostgreSQL) connectivity.
 * Reports database connection status and pool information.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    // In a real implementation, this would use DataSource
    private volatile boolean connected = true;
    private volatile int activeConnections = 5;
    private volatile int maxConnections = 20;
    private volatile String lastError = null;
    
    @Override
    public Health health() {
        if (!connected) {
            return Health.down()
                .withDetail("error", lastError != null ? lastError : "Database connection failed")
                .build();
        }
        
        // Check connection pool utilization
        double utilization = (double) activeConnections / maxConnections;
        
        Health.Builder builder = Health.up()
            .withDetail("status", "connected")
            .withDetail("activeConnections", activeConnections)
            .withDetail("maxConnections", maxConnections)
            .withDetail("poolUtilization", String.format("%.1f%%", utilization * 100));
        
        // Warn if pool is highly utilized
        if (utilization > 0.8) {
            builder.withDetail("warning", "Connection pool utilization is high");
        }
        
        return builder.build();
    }
    
    // Methods for updating status
    public void setConnected(boolean connected) {
        this.connected = connected;
    }
    
    public void setActiveConnections(int activeConnections) {
        this.activeConnections = activeConnections;
    }
    
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
    
    public void setLastError(String error) {
        this.lastError = error;
    }
}


