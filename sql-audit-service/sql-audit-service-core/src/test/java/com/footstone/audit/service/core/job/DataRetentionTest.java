package com.footstone.audit.service.core.job;

import com.footstone.audit.service.core.storage.repository.ExecutionLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = RetentionJob.class)
@TestPropertySource(properties = {
    "audit.storage.retention-days=30"
})
class DataRetentionTest {

    @Autowired
    private RetentionJob retentionJob;

    @MockBean
    private ExecutionLogRepository repository;

    @Test
    void testRetentionJob_shouldRunAndCallDelete() {
        retentionJob.cleanupOldData();
        verify(repository).deleteOlderThan(any(Instant.class));
    }
}
