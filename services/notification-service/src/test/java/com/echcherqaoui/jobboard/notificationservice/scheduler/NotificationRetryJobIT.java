package com.echcherqaoui.jobboard.notificationservice.scheduler;

import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.notificationservice.service.NotificationOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.Mockito.verify;

@SpringBootTest
class NotificationRetryJobIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationRetryJob notificationRetryJob;

    @MockitoSpyBean
    private NotificationOrchestrator orchestrator;

    @Test
    void retryBatches_ShouldDelegateToOrchestrator() {
        notificationRetryJob.retryBatches();

        verify(orchestrator).retryBatches();
    }

    @Test
    void retrySingles_ShouldDelegateToOrchestrator() {
        notificationRetryJob.retrySingles();

        verify(orchestrator).retrySingles();
    }
}