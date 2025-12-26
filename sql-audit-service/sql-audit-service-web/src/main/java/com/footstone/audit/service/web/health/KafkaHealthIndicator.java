package com.footstone.audit.service.web.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Kafka connectivity.
 * Reports Kafka consumer status and connection health.
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {
    
    // In a real implementation, this would be injected
    private volatile boolean connected = true;
    private volatile String lastError = null;
    private volatile long lastMessageTime = System.currentTimeMillis();
    
    @Override
    public Health health() {
        if (!connected) {
            return Health.down()
                .withDetail("error", lastError != null ? lastError : "Not connected")
                .withDetail("lastMessageTime", lastMessageTime)
                .build();
        }
        
        // Check if we've received messages recently (within last 5 minutes)
        long timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime;
        if (timeSinceLastMessage > 300000) { // 5 minutes
            return Health.up()
                .withDetail("status", "connected but idle")
                .withDetail("lastMessageTime", lastMessageTime)
                .withDetail("idleMs", timeSinceLastMessage)
                .build();
        }
        
        return Health.up()
            .withDetail("status", "connected")
            .withDetail("lastMessageTime", lastMessageTime)
            .build();
    }
    
    // Methods for updating status - would be called by Kafka consumer
    public void setConnected(boolean connected) {
        this.connected = connected;
    }
    
    public void setLastError(String error) {
        this.lastError = error;
    }
    
    public void recordMessageReceived() {
        this.lastMessageTime = System.currentTimeMillis();
        this.connected = true;
        this.lastError = null;
    }
}









