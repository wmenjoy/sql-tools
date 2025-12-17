package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration for Pagination Abuse validation rules.
 * Used for YAML configuration loading.
 */
public class PaginationAbuseConfig {

    private boolean enabled = true;
    private RiskLevel riskLevel = RiskLevel.HIGH;
    private LogicalPaginationConfig logicalPagination = new LogicalPaginationConfig();
    private PhysicalNoConditionConfig physicalNoCondition = new PhysicalNoConditionConfig();
    private PhysicalDeepPaginationConfig physicalDeepPagination = new PhysicalDeepPaginationConfig();
    private LargePageSizeConfig largePageSize = new LargePageSizeConfig();
    private NoOrderByConfig noOrderBy = new NoOrderByConfig();
    
    // 限制性字段模式配置
    // 当 WHERE 条件中包含这些字段时，认为结果集已被限制，不需要物理分页
    private List<String> limitingFieldPatterns = Arrays.asList(
        "id",           // 单独的 id 字段
        "[a-z_]+_id",   // 以 _id 结尾的字段（如 user_id, developer_id）
        "[a-z]+Id",     // 驼峰命名的 Id 字段（如 userId, developerId）
        "uuid",         // UUID 字段
        "primary_key",  // 主键字段
        "pk"            // 主键缩写
    );
    
    // 表白名单：这些表不需要检查物理分页（例如：配置表、字典表、小数据量表）
    private List<String> tableWhitelist = new ArrayList<>();
    
    // 表黑名单：这些表必须检查物理分页（优先级高于白名单）
    private List<String> tableBlacklist = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public LogicalPaginationConfig getLogicalPagination() {
        return logicalPagination;
    }

    public void setLogicalPagination(LogicalPaginationConfig logicalPagination) {
        this.logicalPagination = logicalPagination;
    }

    public PhysicalNoConditionConfig getPhysicalNoCondition() {
        return physicalNoCondition;
    }

    public void setPhysicalNoCondition(PhysicalNoConditionConfig physicalNoCondition) {
        this.physicalNoCondition = physicalNoCondition;
    }

    public PhysicalDeepPaginationConfig getPhysicalDeepPagination() {
        return physicalDeepPagination;
    }

    public void setPhysicalDeepPagination(PhysicalDeepPaginationConfig physicalDeepPagination) {
        this.physicalDeepPagination = physicalDeepPagination;
    }

    public LargePageSizeConfig getLargePageSize() {
        return largePageSize;
    }

    public void setLargePageSize(LargePageSizeConfig largePageSize) {
        this.largePageSize = largePageSize;
    }

    public NoOrderByConfig getNoOrderBy() {
        return noOrderBy;
    }

    public void setNoOrderBy(NoOrderByConfig noOrderBy) {
        this.noOrderBy = noOrderBy;
    }

    public List<String> getLimitingFieldPatterns() {
        return limitingFieldPatterns;
    }

    public void setLimitingFieldPatterns(List<String> limitingFieldPatterns) {
        this.limitingFieldPatterns = limitingFieldPatterns;
    }

    public List<String> getTableWhitelist() {
        return tableWhitelist;
    }

    public void setTableWhitelist(List<String> tableWhitelist) {
        this.tableWhitelist = tableWhitelist;
    }

    public List<String> getTableBlacklist() {
        return tableBlacklist;
    }

    public void setTableBlacklist(List<String> tableBlacklist) {
        this.tableBlacklist = tableBlacklist;
    }

    public static class LogicalPaginationConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class PhysicalNoConditionConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class PhysicalDeepPaginationConfig {
        private boolean enabled = true;
        private int maxOffset = 10000;
        private int maxPageNum = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxOffset() {
            return maxOffset;
        }

        public void setMaxOffset(int maxOffset) {
            this.maxOffset = maxOffset;
        }

        public int getMaxPageNum() {
            return maxPageNum;
        }

        public void setMaxPageNum(int maxPageNum) {
            this.maxPageNum = maxPageNum;
        }
    }

    public static class LargePageSizeConfig {
        private boolean enabled = true;
        private int maxPageSize = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxPageSize() {
            return maxPageSize;
        }

        public void setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
        }
    }

    public static class NoOrderByConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
