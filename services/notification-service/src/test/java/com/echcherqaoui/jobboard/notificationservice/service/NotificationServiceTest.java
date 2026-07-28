package com.echcherqaoui.jobboard.notificationservice.service;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationType;
import com.echcherqaoui.jobboard.notificationservice.dto.ApplicationNotificationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    private NotificationOrchestrator orchestrator;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        orchestrator = mock(NotificationOrchestrator.class);
        notificationService = new NotificationService(orchestrator);
    }

    @Test
    void sendWelcome_buildsNotification_withNoExplicitId_relyingOnMongoDefaultId() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.sendWelcome("user-1", "user@example.com", "JOBSEEKER");

        verify(orchestrator).submit(captor.capture());
        Notification n = captor.getValue();

        assertThat(n.getId()).isNull();
        assertThat(n.getRecipientId()).isEqualTo("user-1");
        assertThat(n.getRecipientEmail()).isEqualTo("user@example.com");
        assertThat(n.getType()).isEqualTo(NotificationType.WELCOME);
        assertThat(n.getSubject()).isEqualTo("Welcome to Job Board Platform!");
        assertThat(n.getTemplateName()).isEqualTo("welcome");
        assertThat(n.getTemplateVars()).containsEntry("role", "jobseeker");
    }

    @Test
    void sendWelcome_lowercasesAndStripsUnderscores_fromRoleInTemplateVars() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.sendWelcome("user-1", "user@example.com", "JOB_SEEKER_PRO");

        verify(orchestrator).submit(captor.capture());
        assertThat(captor.getValue().getTemplateVars()).containsEntry("role", "job seeker pro");
    }

    @Test
    void sendApplicationReceived_buildsDeterministicId_fromEventIdAndType() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.sendApplicationReceived(
              "evt-1", "recruiter-1", "Jane Doe", "Backend Engineer", "app-1", "job-1"
        );

        verify(orchestrator).submit(captor.capture());
        Notification n = captor.getValue();

        assertThat(n.getId()).isEqualTo("evt-1:APPLICATION_RECEIVED");
        assertThat(n.getRecipientId()).isEqualTo("recruiter-1");
        assertThat(n.getType()).isEqualTo(NotificationType.APPLICATION_RECEIVED);
        assertThat(n.getSubject()).isEqualTo("New application received for: Backend Engineer");
        assertThat(n.getMessage()).isEqualTo(" Jane Doe applied for Backend Engineer");
        assertThat(n.getJobId()).isEqualTo("job-1");
        assertThat(n.getTemplateName()).isEqualTo("application-received");
        assertThat(n.getTemplateVars())
              .containsEntry("applicantName", "Jane Doe")
              .containsEntry("jobTitle", "Backend Engineer")
              .containsEntry("applicationId", "app-1")
              .containsEntry("jobId", "job-1");
    }

    // ---------- sendApplicationStatusUpdated ----------

    @Test
    void sendApplicationStatusUpdated_buildsReviewedSubject() {
        ApplicationNotificationContext context = new ApplicationNotificationContext(
              "evt-1", "applicant-1", "job-1", "Backend Engineer", "Acme", "REVIEWED", null, "app-1"
        );
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.sendApplicationStatusUpdated(context);

        verify(orchestrator).submit(captor.capture());
        Notification n = captor.getValue();

        assertThat(n.getSubject()).isEqualTo("Your application for Backend Engineer has been reviewed");
        assertThat(n.getId()).isEqualTo("evt-1:APPLICATION_STATUS_UPDATED");
    }

    @Test
    void sendApplicationStatusUpdated_buildsAcceptedSubject() {
        ApplicationNotificationContext context = new ApplicationNotificationContext(
              "evt-2", "applicant-1", "job-1", "Backend Engineer", "Acme", "ACCEPTED", null, "app-1"
        );
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.sendApplicationStatusUpdated(context);

        verify(orchestrator).submit(captor.capture());
        assertThat(captor.getValue().getSubject())
              .isEqualTo("Congratulations! Your application for Backend Engineer was accepted");
    }

    @Test
    void sendApplicationStatusUpdated_buildsFallbackSubject_forUnrecognizedStatus() {
        ApplicationNotificationContext context = new ApplicationNotificationContext(
              "evt-3", "applicant-1", "job-1", "Backend Engineer", "Acme", "WITHDRAWN", null, "app-1"
        );
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.sendApplicationStatusUpdated(context);

        verify(orchestrator).submit(captor.capture());
        assertThat(captor.getValue().getSubject())
              .isEqualTo("Update on your application for Backend Engineer");
    }

    @Test
    void sendApplicationStatusUpdated_isCaseInsensitive_forStatusMatching() {
        ApplicationNotificationContext context = new ApplicationNotificationContext(
              "evt-4", "applicant-1", "job-1", "Backend Engineer", "Acme", "accepted", null, "app-1"
        );
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.sendApplicationStatusUpdated(context);

        verify(orchestrator).submit(captor.capture());
        assertThat(captor.getValue().getSubject())
              .isEqualTo("Congratulations! Your application for Backend Engineer was accepted");
    }

    @Test
    void sendApplicationStatusUpdated_defaultsNoteToEmptyString_whenNoteIsNull() {
        ApplicationNotificationContext context = new ApplicationNotificationContext(
              "evt-5", "applicant-1", "job-1", "Backend Engineer", "Acme", "REVIEWED", null, "app-1"
        );
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.sendApplicationStatusUpdated(context);

        verify(orchestrator).submit(captor.capture());
        assertThat(captor.getValue().getTemplateVars()).containsEntry("note", "");
    }

    @Test
    void sendApplicationStatusUpdated_preservesNote_whenProvided() {
        ApplicationNotificationContext context = new ApplicationNotificationContext(
              "evt-6", "applicant-1", "job-1", "Backend Engineer", "Acme", "REVIEWED", "Great candidate", "app-1"
        );
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.sendApplicationStatusUpdated(context);

        verify(orchestrator).submit(captor.capture());
        assertThat(captor.getValue().getTemplateVars()).containsEntry("note", "Great candidate");
    }

    @Test
    void sendApplicationsCanceled_doesNothing_whenApplicantIdsNull() {
        notificationService.sendApplicationsCanceled("evt-1", "job-1", "Backend Engineer", null);

        verify(orchestrator, never()).submitBatch(anyList(), anyString());
    }

    @Test
    void sendApplicationsCanceled_doesNothing_whenApplicantIdsEmpty() {
        notificationService.sendApplicationsCanceled("evt-1", "job-1", "Backend Engineer", List.of());

        verify(orchestrator, never()).submitBatch(anyList(), anyString());
    }

    @Test
    void sendApplicationsCanceled_buildsOneNotificationPerApplicant_withDistinctIds() {
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);

        notificationService.sendApplicationsCanceled(
              "evt-1", "job-1", "Backend Engineer", List.of("applicant-1", "applicant-2")
        );

        verify(orchestrator).submitBatch(captor.capture(), eq("job-1"));
        List<Notification> notifications = captor.getValue();

        assertThat(notifications).hasSize(2);
        assertThat(notifications.get(0).getId()).isEqualTo("evt-1:applicant-1:APPLICATIONS_CANCELED");
        assertThat(notifications.get(1).getId()).isEqualTo("evt-1:applicant-2:APPLICATIONS_CANCELED");
        assertThat(notifications.get(0).getRecipientId()).isEqualTo("applicant-1");
        assertThat(notifications.get(1).getRecipientId()).isEqualTo("applicant-2");
    }

    @Test
    void sendApplicationsCanceled_hasBrokenSubjectFormatting_literalPercentSign() {
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);

        notificationService.sendApplicationsCanceled(
              "evt-1", "job-1", "Backend Engineer", List.of("applicant-1")
        );

        verify(orchestrator).submitBatch(captor.capture(), eq("job-1"));
        assertThat(captor.getValue().get(0).getSubject())
              .isEqualTo("Your application for Backend Engineer has been cancelled");
    }

    @Test
    void sendApplicationsCanceled_setsSameTemplateVars_acrossAllApplicants() {
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);

        notificationService.sendApplicationsCanceled(
              "evt-1", "job-1", "Backend Engineer", List.of("applicant-1", "applicant-2")
        );

        verify(orchestrator).submitBatch(captor.capture(), eq("job-1"));
        List<Notification> notifications = captor.getValue();

        assertThat(notifications).allSatisfy(n -> {
            assertThat(n.getTemplateVars()).containsEntry("jobTitle", "Backend Engineer");
            assertThat(n.getTemplateVars()).containsEntry("newStatus", "CANCELLED");
        });
    }
}