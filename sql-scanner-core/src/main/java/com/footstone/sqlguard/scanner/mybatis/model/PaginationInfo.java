package com.footstone.sqlguard.scanner.mybatis.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Information about pagination in a SQL statement
 */
public class PaginationInfo {
    
    private final List<PaginationType> types = new ArrayList<>();
    private int pageSize = -1;
    private boolean isDynamic = false;
    private boolean shouldWarnMissingPagination = false;
    
    public void addType(PaginationType type) {
        if (!types.contains(type)) {
            types.add(type);
        }
    }
    
    public List<PaginationType> getTypes() {
        return new ArrayList<>(types);
    }
    
    public boolean hasPagination() {
        return !types.isEmpty();
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
    
    public boolean isDynamic() {
        return isDynamic;
    }
    
    public void setDynamic(boolean dynamic) {
        isDynamic = dynamic;
    }
    
    public boolean shouldWarnMissingPagination() {
        return shouldWarnMissingPagination;
    }
    
    public void setShouldWarnMissingPagination(boolean shouldWarn) {
        this.shouldWarnMissingPagination = shouldWarn;
    }
    
    /**
     * Check if page size is excessive (> 1000)
     */
    public boolean isExcessivePageSize() {
        return pageSize > 1000;
    }
}


