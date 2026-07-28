package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationType;
import com.echcherqaoui.jobboard.notificationservice.grpc.CompanyProfileClient;
import com.echcherqaoui.jobboard.notificationservice.grpc.JobSeekerProfileClient;
import com.echcherqaoui.jobboard.notificationservice.service.EmailService;
import com.echcherqaoui.jobboard.notificationservice.service.NotificationStateService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SingleNotificationSenderImplTest {

    private NotificationStateService stateService;
    private EmailService emailService;
    private CompanyProfileClient companyProfileClient;
    private JobSeekerProfileClient jobSeekerProfileClient;
    private Executor emailTaskExecutor;
    private SingleNotificationSenderImpl sender;

    @BeforeEach
    void setUp() {
        stateService = mock(NotificationStateService.class);
        emailService = mock(EmailService.class);
        companyProfileClient = mock(CompanyProfileClient.class);
        jobSeekerProfileClient = mock(JobSeekerProfileClient.class);
        emailTaskExecutor = mock(Executor.class);
        sender = new SingleNotificationSenderImpl(stateService, emailService, companyProfileClient, jobSeekerProfileClient, emailTaskExecutor);
    }

    private Notification buildClaimed(String id, NotificationType type, String recipientId, String recipientEmail) {
        return new Notification()
              .setId(id)
              .setType(type)
              .setRecipientId(recipientId)
              .setRecipientEmail(recipientEmail);
    }


    @Test
    void executeSend_submitsTaskToExecutor() {
        sender.executeSend("n1");
        verify(emailTaskExecutor).execute(any(Runnable.class));
    }

    @Test
    void attemptSend_returnsEarly_whenClaimReturnsNull() throws MessagingException, UnsupportedEncodingException {
        when(stateService.claim("n1")).thenReturn(null);

        sender.attemptSend("n1");

        verify(emailService, never()).sendHtml(any(Notification.class));
        verify(stateService, never()).markSent(anyString(), anyString());
        verify(stateService, never()).markFailed(anyString(), anyString(), anyString());
    }

    @Test
    void attemptSend_resolvesEmail_whenRecipientEmailMissing_forApplicationReceived() {
        Notification claimed = buildClaimed("n1", NotificationType.APPLICATION_RECEIVED, "recruiter-1", null);
        when(stateService.claim("n1")).thenReturn(claimed);
        when(companyProfileClient.getRecruiterEmail("recruiter-1")).thenReturn("recruiter@example.com");

        sender.attemptSend("n1");

        verify(companyProfileClient).getRecruiterEmail("recruiter-1");
        verify(jobSeekerProfileClient, never()).getJobSeekerEmail(anyString());
        verify(stateService).markSent("n1", "recruiter@example.com");
    }

    @Test
    void attemptSend_resolvesEmail_whenRecipientEmailMissing_forApplicationStatusUpdated() {
        Notification claimed = buildClaimed("n1", NotificationType.APPLICATION_STATUS_UPDATED, "seeker-1", null);
        when(stateService.claim("n1")).thenReturn(claimed);
        when(jobSeekerProfileClient.getJobSeekerEmail("seeker-1")).thenReturn("seeker@example.com");

        sender.attemptSend("n1");

        verify(jobSeekerProfileClient).getJobSeekerEmail("seeker-1");
        verify(stateService).markSent("n1", "seeker@example.com");
    }

    @Test
    void attemptSend_skipsResolution_whenRecipientEmailAlreadyPresent() {
        Notification claimed = buildClaimed("n1", NotificationType.WELCOME, "user-1", "already@example.com");
        when(stateService.claim("n1")).thenReturn(claimed);

        sender.attemptSend("n1");

        verify(companyProfileClient, never()).getRecruiterEmail(anyString());
        verify(jobSeekerProfileClient, never()).getJobSeekerEmail(anyString());
        verify(stateService).markSent("n1", "already@example.com");
    }

    @Test
    void attemptSend_marksFailed_whenWelcomeNotificationHasNoRecipientEmail() throws MessagingException, UnsupportedEncodingException {
        // Documents current behavior: WELCOME with a missing recipientEmail throws
        // IllegalStateException from resolveEmail, but it's caught by the generic catch
        // block and routed through the normal retry/markFailed path - it does NOT
        // escalate distinctly despite reading like a hard invariant violation.
        Notification claimed = buildClaimed("n1", NotificationType.WELCOME, "user-1", null);
        when(stateService.claim("n1")).thenReturn(claimed);

        sender.attemptSend("n1");

        verify(stateService).markFailed(
              eq("n1"),
              eq("WELCOME notifications must carry recipientEmail already"),
              isNull()
        );
        verify(emailService, never()).sendHtml(any(Notification.class));
    }

    @Test
    void attemptSend_marksFailed_whenTypeHasNoResolver() {
        Notification claimed = buildClaimed("n1", NotificationType.APPLICATIONS_CANCELED, "user-1", null);
        when(stateService.claim("n1")).thenReturn(claimed);

        sender.attemptSend("n1");

        verify(stateService).markFailed(
              eq("n1"),
              eq("No email resolver for type: APPLICATIONS_CANCELED"),
              isNull()
        );
    }

    @Test
    void attemptSend_marksFailed_withResolvedEmail_whenSendHtmlThrowsAfterResolution() throws MessagingException, UnsupportedEncodingException {
        Notification claimed = buildClaimed("n1", NotificationType.APPLICATION_RECEIVED, "recruiter-1", null);
        when(stateService.claim("n1")).thenReturn(claimed);
        when(companyProfileClient.getRecruiterEmail("recruiter-1")).thenReturn("recruiter@example.com");
        doThrow(new RuntimeException("smtp down")).when(emailService).sendHtml(any(Notification.class));

        sender.attemptSend("n1");

        verify(stateService).markFailed("n1", "smtp down", "recruiter@example.com");
        verify(stateService, never()).markSent(anyString(), anyString());
    }

    @Test
    void attemptSend_marksFailed_withNullEmail_whenResolutionItselfThrows() throws MessagingException, UnsupportedEncodingException {
        Notification claimed = buildClaimed("n1", NotificationType.APPLICATION_RECEIVED, "recruiter-1", null);
        when(stateService.claim("n1")).thenReturn(claimed);
        when(companyProfileClient.getRecruiterEmail("recruiter-1")).thenThrow(new RuntimeException("grpc timeout"));

        sender.attemptSend("n1");

        verify(stateService).markFailed(eq("n1"), eq("grpc timeout"), isNull());
        verify(emailService, never()).sendHtml(any(Notification.class));
    }
}