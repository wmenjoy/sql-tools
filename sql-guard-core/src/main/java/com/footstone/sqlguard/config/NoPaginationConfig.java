package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for No Pagination validation rule.
 * Used for YAML configuration loading.
 * Detects queries that should have pagination but don't.
 */
public class NoPaginationConfig {

  private boolean enabled = true;
  private RiskLevel riskLevel = RiskLevel.MEDIUM;
  private boolean enforceForAllQueries = false;
  private List<String> whitelistMapperIds = new ArrayList<>();
  private List<String> whitelistTables = new ArrayList<>();
  private List<String> uniqueKeyFields = new ArrayList<>();

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

  public boolean isEnforceForAllQueries() {
    return enforceForAllQueries;
  }

  public void setEnforceForAllQueries(boolean enforceForAllQueries) {
    this.enforceForAllQueries = enforceForAllQueries;
  }

  public List<String> getWhitelistMapperIds() {
    return whitelistMapperIds;
  }

  public void setWhitelistMapperIds(List<String> whitelistMapperIds) {
    this.whitelistMapperIds = whitelistMapperIds;
  }

  public List<String> getWhitelistTables() {
    return whitelistTables;
  }

  public void setWhitelistTables(List<String> whitelistTables) {
    this.whitelistTables = whitelistTables;
  }

  public List<String> getUniqueKeyFields() {
    return uniqueKeyFields;
  }

  public void setUniqueKeyFields(List<String> uniqueKeyFields) {
    this.uniqueKeyFields = uniqueKeyFields;
  }
}
