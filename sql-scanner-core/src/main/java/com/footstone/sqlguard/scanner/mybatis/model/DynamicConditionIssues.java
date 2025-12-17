package com.footstone.sqlguard.scanner.mybatis.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Issues found in dynamic SQL conditions
 */
public class DynamicConditionIssues {
    
    private boolean whereClauseMightDisappear = false;
    private boolean hasAlwaysTrueCondition = false;
    private boolean hasNoWhereClause = false;
    private final List<String> issues = new ArrayList<>();
    
    public boolean hasIssues() {
        return whereClauseMightDisappear || hasAlwaysTrueCondition || hasNoWhereClause;
    }
    
    public boolean hasWhereClauseMightDisappear() {
        return whereClauseMightDisappear;
    }
    
    public void setWhereClauseMightDisappear(boolean whereClauseMightDisappear) {
        this.whereClauseMightDisappear = whereClauseMightDisappear;
        if (whereClauseMightDisappear) {
            issues.add("WHERE clause might disappear when all conditions are null");
        }
    }
    
    public boolean hasAlwaysTrueCondition() {
        return hasAlwaysTrueCondition;
    }
    
    public void setHasAlwaysTrueCondition(boolean hasAlwaysTrueCondition) {
        this.hasAlwaysTrueCondition = hasAlwaysTrueCondition;
        if (hasAlwaysTrueCondition) {
            issues.add("Contains always-true condition (e.g., 1=1)");
        }
    }
    
    public boolean hasNoWhereClause() {
        return hasNoWhereClause;
    }
    
    public void setHasNoWhereClause(boolean hasNoWhereClause) {
        this.hasNoWhereClause = hasNoWhereClause;
        if (hasNoWhereClause) {
            issues.add("SELECT query without WHERE clause");
        }
    }
    
    public List<String> getIssues() {
        return new ArrayList<>(issues);
    }
}




