package com.echcherqaoui.jobboard.applicationservice.service;

import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatusHistory;
import com.echcherqaoui.jobboard.applicationservice.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.CANCELED;
import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApplicationDataAccessTest {

    private ApplicationRepository applicationRepository;
    private ApplicationOutboxService applicationOutboxService;
    private ApplicationDataAccess dataAccess;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRepository.class);
        applicationOutboxService = mock(ApplicationOutboxService.class);
        dataAccess = new ApplicationDataAccess(applicationRepository, applicationOutboxService);
    }

    private Application pendingApp(UUID applicantId) {
        Application app = new Application();
        app.setApplicantId(applicantId);
        app.setStatus(PENDING);
        return app;
    }

    @Test
    void noPendingApplications_doesNothingAndReturnsEarly() {
        UUID jobId = UUID.randomUUID();
        when(applicationRepository.findByJobIdAndStatus(jobId, PENDING)).thenReturn(List.of());

        dataAccess.bulkRejectAndExecute(jobId, "Backend Engineer");

        verify(applicationRepository, never()).saveAll(any());
        verifyNoInteractions(applicationOutboxService);
    }

    @Test
    void pendingApplications_transitionToCanceledWithHistoryEntry() {
        UUID jobId = UUID.randomUUID();
        UUID applicant1 = UUID.randomUUID();
        UUID applicant2 = UUID.randomUUID();
        Application app1 = pendingApp(applicant1);
        Application app2 = pendingApp(applicant2);
        when(applicationRepository.findByJobIdAndStatus(jobId, PENDING)).thenReturn(List.of(app1, app2));
        when(applicationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        dataAccess.bulkRejectAndExecute(jobId, "Backend Engineer");

        assertThat(app1.getStatus()).isEqualTo(CANCELED);
        assertThat(app2.getStatus()).isEqualTo(CANCELED);

        assertThat(app1.getStatusHistory()).hasSize(1);
        ApplicationStatusHistory history = app1.getStatusHistory().get(0);
        assertThat(history.getOldStatus()).isEqualTo(PENDING);
        assertThat(history.getNewStatus()).isEqualTo(CANCELED);
        assertThat(history.getNote()).isEqualTo("Job has been closed");
        // Documents current behavior: system-driven cancellation has no human actor.
        // If changedBy is a non-null DB column, this will only surface in an integration
        // test against a real schema — a mocked repository won't catch it.
        assertThat(history.getChangedBy()).isNull();
    }

    @Test
    void savedApplications_feedOutboxWithApplicantIds() {
        UUID jobId = UUID.randomUUID();
        UUID applicant1 = UUID.randomUUID();
        UUID applicant2 = UUID.randomUUID();
        Application app1 = pendingApp(applicant1);
        Application app2 = pendingApp(applicant2);
        when(applicationRepository.findByJobIdAndStatus(jobId, PENDING)).thenReturn(List.of(app1, app2));
        when(applicationRepository.saveAll(anyList())).thenReturn(List.of(app1, app2));

        dataAccess.bulkRejectAndExecute(jobId, "Backend Engineer");

        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(applicationOutboxService).publishJobApplicationsCanceled(eq(jobId), eq("Backend Engineer"), idsCaptor.capture());

        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(
              applicant1.toString(), applicant2.toString()
        );
    }

    @Test
    void queriesOnlyPendingApplicationsForTheJob() {
        UUID jobId = UUID.randomUUID();
        when(applicationRepository.findByJobIdAndStatus(jobId, PENDING)).thenReturn(List.of());

        dataAccess.bulkRejectAndExecute(jobId, "Backend Engineer");

        verify(applicationRepository).findByJobIdAndStatus(jobId, PENDING);
        verify(applicationRepository, never()).findByJobIdAndStatus(eq(jobId), argThat(s -> s != PENDING));
    }
}