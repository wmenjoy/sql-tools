package com.footstone.audit.service.web.controller;

import com.footstone.audit.service.web.exception.ResourceNotFoundException;
import com.footstone.audit.service.web.service.AuditReportQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditReportController.class)
class GlobalExceptionHandlerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private AuditReportQueryService queryService;
    
    @Test
    void testNotFound_shouldReturn404() throws Exception {
        // Arrange
        when(queryService.getAuditById("non-existent"))
            .thenThrow(new ResourceNotFoundException("Audit report not found: non-existent"));
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits/{reportId}", "non-existent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.message", containsString("not found")));
    }
    
    @Test
    void testBadRequest_shouldReturn400() throws Exception {
        // Arrange
        when(queryService.getAuditById("bad-id"))
            .thenThrow(new IllegalArgumentException("Invalid ID format"));
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits/{reportId}", "bad-id"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
            .andExpect(jsonPath("$.message", containsString("Invalid")));
    }
    
    @Test
    void testInternalError_shouldReturn500() throws Exception {
        // Arrange
        when(queryService.getAuditById("error"))
            .thenThrow(new RuntimeException("Unexpected database error"));
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits/{reportId}", "error"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code", is("INTERNAL_ERROR")))
            .andExpect(jsonPath("$.message", containsString("unexpected")));
    }
    
    @Test
    void testErrorResponse_shouldHaveStandardFormat() throws Exception {
        // Arrange
        when(queryService.getAuditById("test"))
            .thenThrow(new ResourceNotFoundException("Resource not found"));
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/audits/{reportId}", "test"))
            .andExpect(status().isNotFound())
            // Verify standard error response format
            .andExpect(jsonPath("$.code", notNullValue()))
            .andExpect(jsonPath("$.message", notNullValue()))
            .andExpect(jsonPath("$.timestamp", notNullValue()));
    }
}








