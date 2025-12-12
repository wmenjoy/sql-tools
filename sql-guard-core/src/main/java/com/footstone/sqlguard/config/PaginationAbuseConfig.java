package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;

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

    public void setLargePageSize(LargePageSizeConfig physicalLargePageSize) {
        this.largePageSize = physicalLargePageSize;
    }

    public NoOrderByConfig getNoOrderBy() {
        return noOrderBy;
    }

    public void setNoOrderBy(NoOrderByConfig noOrderBy) {
        this.noOrderBy = noOrderBy;
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
