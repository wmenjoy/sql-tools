package com.footstone.audit.service.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Configuration
@ConfigurationProperties(prefix = "audit.engine")
public class AuditEngineConfig {
    private long checkerTimeoutMs = 200;
    private Map<String, CheckerConfig> checkers = new HashMap<>();
    private List<String> whitelistRules = new ArrayList<>();
    private List<Pattern> whitelistPatterns;

    public long getCheckerTimeoutMs() {
        return checkerTimeoutMs;
    }

    public void setCheckerTimeoutMs(long checkerTimeoutMs) {
        this.checkerTimeoutMs = checkerTimeoutMs;
    }

    public Map<String, CheckerConfig> getCheckers() {
        return checkers;
    }

    public void setCheckers(Map<String, CheckerConfig> checkers) {
        this.checkers = checkers;
    }

    public List<String> getWhitelistRules() {
        return whitelistRules;
    }

    public void setWhitelistRules(List<String> whitelistRules) {
        this.whitelistRules = whitelistRules;
        this.whitelistPatterns = null; // reset cache
    }

    public boolean isCheckerEnabled(String checkerId) {
        CheckerConfig config = checkers.get(checkerId);
        return config == null || config.isEnabled();
    }
    
    public boolean isWhitelisted(String sql) {
        if (whitelistPatterns == null) {
            whitelistPatterns = new ArrayList<>();
            for (String rule : whitelistRules) {
                try {
                    whitelistPatterns.add(Pattern.compile(rule));
                } catch (Exception e) {
                    // Log error
                }
            }
        }
        
        for (Pattern pattern : whitelistPatterns) {
            if (pattern.matcher(sql).find()) {
                return true;
            }
        }
        return false;
    }

    public static class CheckerConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
