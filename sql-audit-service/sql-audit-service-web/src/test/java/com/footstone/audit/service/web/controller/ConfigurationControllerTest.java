package com.footstone.audit.service.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footstone.audit.service.web.dto.*;
import com.footstone.audit.service.web.exception.ResourceNotFoundException;
import com.footstone.audit.service.web.service.CheckerConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigurationController.class)
class ConfigurationControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private CheckerConfigurationService configService;
    
    @Test
    void testGetCheckers_shouldReturnAllCheckers() throws Exception {
        // Arrange
        List<CheckerInfoDto> checkers = List.of(
            new CheckerInfoDto("slow_query", "Slow Query Checker", "Detects slow queries", true, "Performance"),
            new CheckerInfoDto("full_table_scan", "Full Table Scan", "Detects full scans", true, "Performance")
        );
        
        when(configService.getAllCheckers()).thenReturn(checkers);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/checkers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].checkerId", is("slow_query")))
            .andExpect(jsonPath("$[0].enabled", is(true)));
    }
    
    @Test
    void testGetCheckerConfig_shouldReturnConfig() throws Exception {
        // Arrange
        CheckerConfigDto config = new CheckerConfigDto(
            "slow_query",
            true,
            Map.of("thresholdMs", 1000),
            List.of()
        );
        
        when(configService.getCheckerConfig("slow_query")).thenReturn(config);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/checkers/{checkerId}/config", "slow_query"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkerId", is("slow_query")))
            .andExpect(jsonPath("$.enabled", is(true)))
            .andExpect(jsonPath("$.thresholds.thresholdMs", is(1000)));
    }
    
    @Test
    void testUpdateCheckerConfig_shouldPersist() throws Exception {
        // Arrange
        CheckerConfigUpdateDto updateDto = new CheckerConfigUpdateDto(
            false,
            Map.of("thresholdMs", 2000)
        );
        
        CheckerConfigDto updatedConfig = new CheckerConfigDto(
            "slow_query",
            false,
            Map.of("thresholdMs", 2000),
            List.of()
        );
        
        when(configService.updateCheckerConfig(eq("slow_query"), any(CheckerConfigUpdateDto.class)))
            .thenReturn(updatedConfig);
        
        // Act & Assert
        mockMvc.perform(put("/api/v1/checkers/{checkerId}/config", "slow_query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled", is(false)))
            .andExpect(jsonPath("$.thresholds.thresholdMs", is(2000)));
    }
    
    @Test
    void testEnableChecker_shouldActivate() throws Exception {
        // Arrange
        doNothing().when(configService).enableChecker("slow_query");
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/checkers/{checkerId}/enable", "slow_query"))
            .andExpect(status().isOk());
        
        verify(configService).enableChecker("slow_query");
    }
    
    @Test
    void testDisableChecker_shouldDeactivate() throws Exception {
        // Arrange
        doNothing().when(configService).disableChecker("slow_query");
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/checkers/{checkerId}/disable", "slow_query"))
            .andExpect(status().isOk());
        
        verify(configService).disableChecker("slow_query");
    }
    
    @Test
    void testUpdateThreshold_shouldApply() throws Exception {
        // Arrange
        doNothing().when(configService).updateThreshold(eq("slow_query"), eq("thresholdMs"), any());
        
        // Act & Assert
        mockMvc.perform(put("/api/v1/checkers/{checkerId}/threshold/{thresholdKey}", "slow_query", "thresholdMs")
                .param("value", "1500"))
            .andExpect(status().isOk());
        
        verify(configService).updateThreshold("slow_query", "thresholdMs", "1500");
    }
    
    @Test
    void testAddWhitelistRule_shouldAdd() throws Exception {
        // Arrange
        WhitelistRuleDto rule = new WhitelistRuleDto(
            null,
            "SQL_PATTERN",
            "SELECT.*FROM users",
            "Allow user queries",
            true
        );
        
        doNothing().when(configService).addWhitelistRule(eq("slow_query"), any(WhitelistRuleDto.class));
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/checkers/{checkerId}/whitelist", "slow_query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rule)))
            .andExpect(status().isOk());
        
        verify(configService).addWhitelistRule(eq("slow_query"), any(WhitelistRuleDto.class));
    }
    
    @Test
    void testRemoveWhitelistRule_shouldRemove() throws Exception {
        // Arrange
        doNothing().when(configService).removeWhitelistRule("slow_query", "rule-123");
        
        // Act & Assert
        mockMvc.perform(delete("/api/v1/checkers/{checkerId}/whitelist/{ruleId}", "slow_query", "rule-123"))
            .andExpect(status().isOk());
        
        verify(configService).removeWhitelistRule("slow_query", "rule-123");
    }
    
    @Test
    void testConfigValidation_invalidThreshold_shouldReject() throws Exception {
        // Arrange
        CheckerConfigUpdateDto updateDto = new CheckerConfigUpdateDto(
            null,
            Map.of("thresholdMs", -100)  // Invalid negative threshold
        );
        
        when(configService.updateCheckerConfig(eq("slow_query"), any(CheckerConfigUpdateDto.class)))
            .thenThrow(new IllegalArgumentException("Invalid threshold value: must be non-negative"));
        
        // Act & Assert
        mockMvc.perform(put("/api/v1/checkers/{checkerId}/config", "slow_query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void testConfigAuditLog_shouldRecordChanges() throws Exception {
        // Arrange
        List<CheckerConfigurationService.ConfigAuditEntry> auditLog = List.of(
            new CheckerConfigurationService.ConfigAuditEntry(
                System.currentTimeMillis(), "slow_query", "UPDATE_CONFIG", "thresholdMs=2000"),
            new CheckerConfigurationService.ConfigAuditEntry(
                System.currentTimeMillis(), "full_table_scan", "DISABLE", null)
        );
        
        when(configService.getConfigAuditLog()).thenReturn(auditLog);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/checkers/audit-log"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].checkerId", is("slow_query")))
            .andExpect(jsonPath("$[0].action", is("UPDATE_CONFIG")));
    }
}










