package com.footstone.audit.service.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footstone.audit.service.core.model.AuditReport;
import com.footstone.audit.service.core.model.CheckerResult;
import com.footstone.audit.service.web.exception.ResourceNotFoundException;
import com.footstone.audit.service.web.service.AuditReportQueryService;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditReportController.class)
class AuditReportControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private AuditReportQueryService queryService;
    
    @Test
    void testGetAudits_shouldReturnPagedResults() throws Exception {
        // Arrange
        List<AuditReport> reports = createSampleReports(25);
        Page<AuditReport> page = new PageImpl<>(reports.subList(0, 20), PageRequest.of(0, 20), 25);
        
        when(queryService.getAudits(any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(20)))
            .andExpect(jsonPath("$.totalElements", is(25)))
            .andExpect(jsonPath("$.totalPages", is(2)))
            .andExpect(jsonPath("$.size", is(20)))
            .andExpect(jsonPath("$.number", is(0)));
    }
    
    @Test
    void testGetAudits_withFilters_shouldFilter() throws Exception {
        // Arrange
        String sqlId = "UserMapper.selectById";
        List<AuditReport> filteredReports = createSampleReports(5);
        Page<AuditReport> page = new PageImpl<>(filteredReports, PageRequest.of(0, 20), 5);
        
        when(queryService.getAudits(eq(sqlId), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits")
                .param("sqlId", sqlId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(5)))
            .andExpect(jsonPath("$.totalElements", is(5)));
    }
    
    @Test
    void testGetAudits_withSorting_shouldSort() throws Exception {
        // Arrange
        List<AuditReport> reports = createSampleReports(10);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Page<AuditReport> page = new PageImpl<>(reports, PageRequest.of(0, 20, sort), 10);
        
        when(queryService.getAudits(any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits")
                .param("sort", "createdAt,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(10)));
    }
    
    @Test
    void testGetAuditById_existingId_shouldReturn() throws Exception {
        // Arrange
        String reportId = "report-123";
        AuditReport report = createSampleReports(1).get(0);
        
        when(queryService.getAuditById(reportId))
            .thenReturn(report);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits/{reportId}", reportId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reportId", notNullValue()))
            .andExpect(jsonPath("$.sqlId", notNullValue()));
    }
    
    @Test
    void testGetAuditById_nonExistingId_shouldReturn404() throws Exception {
        // Arrange
        String reportId = "non-existent";
        
        when(queryService.getAuditById(reportId))
            .thenThrow(new ResourceNotFoundException("Audit report not found: " + reportId));
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits/{reportId}", reportId))
            .andExpect(status().isNotFound());
    }
    
    @Test
    void testGetAuditsBySqlId_shouldReturnMatches() throws Exception {
        // Arrange
        String sqlId = "UserMapper.selectAll";
        List<AuditReport> reports = createSampleReports(3);
        Page<AuditReport> page = new PageImpl<>(reports, PageRequest.of(0, 20), 3);
        
        when(queryService.getAudits(eq(sqlId), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits")
                .param("sqlId", sqlId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(3)));
    }
    
    @Test
    void testGetAuditsByRiskLevel_shouldFilter() throws Exception {
        // Arrange
        RiskLevel riskLevel = RiskLevel.HIGH;
        List<AuditReport> reports = createSampleReports(7);
        Page<AuditReport> page = new PageImpl<>(reports, PageRequest.of(0, 20), 7);
        
        when(queryService.getAudits(any(), eq(riskLevel), any(), any(), any(Pageable.class)))
            .thenReturn(page);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits")
                .param("riskLevel", "HIGH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(7)));
    }
    
    @Test
    void testGetAuditsByTimeRange_shouldFilter() throws Exception {
        // Arrange
        Instant startTime = Instant.parse("2025-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2025-01-02T00:00:00Z");
        List<AuditReport> reports = createSampleReports(15);
        Page<AuditReport> page = new PageImpl<>(reports, PageRequest.of(0, 20), 15);
        
        when(queryService.getAudits(any(), any(), eq(startTime), eq(endTime), any(Pageable.class)))
            .thenReturn(page);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits")
                .param("startTime", "2025-01-01T00:00:00Z")
                .param("endTime", "2025-01-02T00:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(15)));
    }
    
    @Test
    void testPagination_defaultPageSize_shouldBe20() throws Exception {
        // Arrange
        List<AuditReport> reports = createSampleReports(20);
        Page<AuditReport> page = new PageImpl<>(reports, PageRequest.of(0, 20), 50);
        
        when(queryService.getAudits(any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size", is(20)));
    }
    
    @Test
    void testPagination_maxPageSize_shouldBe100() throws Exception {
        // Arrange
        List<AuditReport> reports = createSampleReports(100);
        Page<AuditReport> page = new PageImpl<>(reports, PageRequest.of(0, 100), 150);
        
        when(queryService.getAudits(any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits")
                .param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size", is(100)));
    }
    
    // Helper method to create sample reports
    private List<AuditReport> createSampleReports(int count) {
        List<AuditReport> reports = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            AuditEvent event = AuditEvent.builder()
                .statementId("UserMapper.selectById")
                .sql("SELECT * FROM users WHERE id = ?")
                .sqlType(com.footstone.sqlguard.core.model.SqlCommandType.SELECT)
                .executionTimeMs(50L + i)
                .rowsAffected(1)
                .timestamp(Instant.now().minusSeconds(i * 60))
                .build();
            
            RiskScore riskScore = RiskScore.builder()
                .severity(RiskLevel.MEDIUM)
                .confidence(80)
                .justification("Test risk")
                .build();
            
            List<CheckerResult> checkerResults = List.of(
                CheckerResult.success("slow_query", riskScore)
            );
            
            AuditReport report = new AuditReport(
                "report-" + i,
                "UserMapper.selectById",
                event,
                checkerResults,
                riskScore,
                Instant.now().minusSeconds(i * 60)
            );
            
            reports.add(report);
        }
        
        return reports;
    }
}
