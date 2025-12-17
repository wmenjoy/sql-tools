package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.scanner.mybatis.model.*;

import java.util.regex.Pattern;

/**
 * Analyzer for dynamic SQL conditions
 * 
 * Detects:
 * - WHERE clause that might disappear
 * - Conditions that are always true
 * - Missing WHERE clause
 */
public class DynamicConditionAnalyzer {
    
    private static final Pattern WHERE_TAG_PATTERN = Pattern.compile(
        "<where>.*?</where>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern IF_TAG_PATTERN = Pattern.compile(
        "<if\\s+test=['\"].*?['\"]>.*?</if>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern CHOOSE_TAG_PATTERN = Pattern.compile(
        "<choose>.*?</choose>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern OTHERWISE_TAG_PATTERN = Pattern.compile(
        "<otherwise>.*?</otherwise>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern ALWAYS_TRUE_PATTERN = Pattern.compile(
        "\\b(1\\s*=\\s*1|true\\s*=\\s*true)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern WHERE_KEYWORD_PATTERN = Pattern.compile(
        "\\bWHERE\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "\\bSELECT\\b.*?\\bFROM\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    /**
     * Analyze dynamic condition issues
     * 
     * @param combined Combined analysis result
     * @return Dynamic condition issues
     */
    public DynamicConditionIssues analyze(CombinedAnalysisResult combined) {
        DynamicConditionIssues issues = new DynamicConditionIssues();
        
        String rawSql = combined.getRawSql();
        if (rawSql == null || rawSql.isEmpty()) {
            return issues;
        }
        
        // Check for always-true conditions
        if (ALWAYS_TRUE_PATTERN.matcher(rawSql).find()) {
            issues.setHasAlwaysTrueCondition(true);
        }
        
        // Check for WHERE clause issues
        analyzeWhereClause(rawSql, issues);
        
        return issues;
    }
    
    /**
     * Analyze WHERE clause
     */
    private void analyzeWhereClause(String rawSql, DynamicConditionIssues issues) {
        // Check if this is a SELECT query
        if (!SELECT_PATTERN.matcher(rawSql).find()) {
            return;  // Not a SELECT query
        }
        
        // Check if has <where> tag
        if (WHERE_TAG_PATTERN.matcher(rawSql).find()) {
            analyzeWhereTag(rawSql, issues);
            return;
        }
        
        // Check if has WHERE keyword
        if (WHERE_KEYWORD_PATTERN.matcher(rawSql).find()) {
            // Has static WHERE clause
            return;
        }
        
        // No WHERE clause at all
        issues.setHasNoWhereClause(true);
    }
    
    /**
     * Analyze <where> tag
     */
    private void analyzeWhereTag(String rawSql, DynamicConditionIssues issues) {
        // Extract <where> content
        java.util.regex.Matcher whereMatcher = WHERE_TAG_PATTERN.matcher(rawSql);
        if (!whereMatcher.find()) {
            return;
        }
        
        String whereContent = whereMatcher.group();
        
        // Check if all conditions are optional (all inside <if> or <choose>)
        boolean hasStaticCondition = hasStaticCondition(whereContent);
        boolean hasChooseWithOtherwise = hasChooseWithOtherwise(whereContent);
        boolean hasChooseWithoutOtherwise = hasChooseWithoutOtherwise(whereContent);
        
        // WHERE might disappear if:
        // 1. No static condition AND
        // 2. Either has <choose> without <otherwise> OR has no <choose> with <otherwise>
        if (!hasStaticCondition && !hasChooseWithOtherwise) {
            issues.setWhereClauseMightDisappear(true);
        } else if (hasChooseWithoutOtherwise) {
            issues.setWhereClauseMightDisappear(true);
        }
    }
    
    /**
     * Check if WHERE has any static (non-conditional) condition
     */
    private boolean hasStaticCondition(String whereContent) {
        // Remove all <if> tags
        String withoutIf = IF_TAG_PATTERN.matcher(whereContent).replaceAll("");
        
        // Remove all <choose> tags
        String withoutChoose = CHOOSE_TAG_PATTERN.matcher(withoutIf).replaceAll("");
        
        // Remove <where> tags
        String withoutWhere = withoutChoose.replaceAll("</?where>", "");
        
        // Check if there's any meaningful content left
        String trimmed = withoutWhere.trim();
        
        // If there's content that looks like a condition, it's static
        return trimmed.length() > 0 && 
               (trimmed.contains("=") || trimmed.contains(">") || trimmed.contains("<"));
    }
    
    /**
     * Check if has <choose> WITH <otherwise>
     */
    private boolean hasChooseWithOtherwise(String whereContent) {
        java.util.regex.Matcher chooseMatcher = CHOOSE_TAG_PATTERN.matcher(whereContent);
        
        while (chooseMatcher.find()) {
            String chooseContent = chooseMatcher.group();
            
            // Check if this <choose> has <otherwise>
            if (OTHERWISE_TAG_PATTERN.matcher(chooseContent).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if has <choose> WITHOUT <otherwise>
     */
    private boolean hasChooseWithoutOtherwise(String whereContent) {
        java.util.regex.Matcher chooseMatcher = CHOOSE_TAG_PATTERN.matcher(whereContent);
        
        while (chooseMatcher.find()) {
            String chooseContent = chooseMatcher.group();
            
            // Check if this <choose> has <otherwise>
            if (!OTHERWISE_TAG_PATTERN.matcher(chooseContent).find()) {
                return true;
            }
        }
        
        return false;
    }
}

