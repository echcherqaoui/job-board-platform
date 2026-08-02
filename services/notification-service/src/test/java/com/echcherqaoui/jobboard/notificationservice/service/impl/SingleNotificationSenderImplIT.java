package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.notificationservice.config.NotificationProperties;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationType;
import com.echcherqaoui.jobboard.notificationservice.grpc.CompanyProfileClient;
import com.echcherqaoui.jobboard.notificationservice.grpc.JobSeekerProfileClient;
import com.echcherqaoui.jobboard.notificationservice.service.EmailService;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.FAILED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.PENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class SingleNotificationSenderImplIT extends AbstractIntegrationTest {

    static GreenMail greenMail;

    @BeforeAll
    static void startGreenMail() {
        greenMail = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
        greenMail.start();
    }

    @AfterAll
    static void stopGreenMail() {
        if (greenMail != null)
            greenMail.stop();
    }

    @DynamicPropertySource
    static void configureMailHost(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
    }

    @Autowired
    private SingleNotificationSenderImpl singleNotificationSender;

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private CompanyProfileClient companyProfileClient;

    @MockitoBean
    private JobSeekerProfileClient jobSeekerProfileClient;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean(name = "emailTaskExecutor")
    private Executor emailTaskExecutor;

    @BeforeEach
    void cleanDatabase() {
        mongoTemplate.remove(new Query(), Notification.class);
    }

    @AfterEach
    void purgeMailbox() throws Exception {
        if (greenMail != null && greenMail.isRunning())
            greenMail.purgeEmailFromAllMailboxes();
    }

    @Nested
    class AttemptSend {

        @Test
        void attemptSend_WhenEmailMissingForRecruiter_ShouldResolveViaCompanyGrpcAndMarkSent() throws Exception {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("recruiter-123")
                  .setType(NotificationType.APPLICATION_RECEIVED)
                  .setStatus(PENDING)
                  .setSubject("New Application")
                  .setTemplateName("welcome"));

            when(companyProfileClient.getRecruiterEmail("recruiter-123")).thenReturn("hr@company.com");

            singleNotificationSender.attemptSend(notification.getId());

            Notification updated = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(SENT);
            assertThat(updated.getRecipientEmail()).isEqualTo("hr@company.com");
            assertThat(updated.getSentAt()).isNotNull();

            verify(companyProfileClient).getRecruiterEmail("recruiter-123");
            verify(emailService).sendHtml(any());
        }

        @Test
        void attemptSend_WhenEmailMissingForJobSeeker_ShouldResolveViaJobSeekerGrpcAndMarkSent() throws Exception {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("jobseeker-123")
                  .setType(NotificationType.APPLICATION_STATUS_UPDATED)
                  .setStatus(PENDING)
                  .setSubject("Status Update")
                  .setTemplateName("status_change"));

            when(jobSeekerProfileClient.getJobSeekerEmail("jobseeker-123")).thenReturn("applicant@domain.com");

            singleNotificationSender.attemptSend(notification.getId());

            Notification updated = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(SENT);
            assertThat(updated.getRecipientEmail()).isEqualTo("applicant@domain.com");

            verify(jobSeekerProfileClient).getJobSeekerEmail("jobseeker-123");
            verify(emailService).sendHtml(any());
        }

        @Test
        void attemptSend_WhenEmailAlreadyPresent_ShouldSkipGrpcResolutionAndMarkSent() throws Exception {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("user-789")
                  .setRecipientEmail("direct@domain.com")
                  .setType(NotificationType.APPLICATION_STATUS_UPDATED)
                  .setStatus(PENDING)
                  .setSubject("Direct Email")
                  .setTemplateName("status_change"));

            singleNotificationSender.attemptSend(notification.getId());

            Notification updated = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(SENT);
            assertThat(updated.getRecipientEmail()).isEqualTo("direct@domain.com");

            verifyNoInteractions(companyProfileClient, jobSeekerProfileClient);
            verify(emailService).sendHtml(any());
        }

        @Test
        void attemptSend_WhenWelcomeNotificationMissingEmail_ShouldFailWithIllegalStateException() throws MessagingException, UnsupportedEncodingException {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("user-100")
                  .setType(NotificationType.WELCOME)
                  .setStatus(PENDING)
                  .setSubject("Welcome!")
                  .setTemplateName("welcome"));

            singleNotificationSender.attemptSend(notification.getId());

            Notification updated = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getLastError()).contains("WELCOME notifications must carry recipientEmail already");
            verify(emailService, never()).sendHtml(any());
        }

        @Test
        void attemptSend_WhenNotificationIsAlreadyClaimed_ShouldDoNothing() {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("user-200")
                  .setStatus(SENDING)
                  .setAttempts(1)
                  .setSubject("In Progress")
                  .setTemplateName("welcome"));

            singleNotificationSender.attemptSend(notification.getId());

            verifyNoInteractions(emailService, companyProfileClient, jobSeekerProfileClient);

            Notification dbState = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(dbState).isNotNull();
            assertThat(dbState.getStatus()).isEqualTo(SENDING);
        }

        @Test
        void attemptSend_WhenEmailSendingFailsAndBelowMaxAttempts_ShouldResetStatusToPending() throws Exception {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("jobseeker-456")
                  .setRecipientEmail("applicant@acme.com")
                  .setType(NotificationType.APPLICATION_STATUS_UPDATED)
                  .setStatus(PENDING)
                  .setAttempts(0)
                  .setSubject("Application Update")
                  .setTemplateName("welcome"));

            doThrow(new RuntimeException("SMTP Server Unavailable"))
                  .when(emailService).sendHtml(any());

            singleNotificationSender.attemptSend(notification.getId());

            Notification updated = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(PENDING);
            assertThat(updated.getAttempts()).isEqualTo(1);
            assertThat(updated.getLastError()).isEqualTo("SMTP Server Unavailable");
        }

        @Test
        void attemptSend_WhenEmailSendingFailsAndMaxAttemptsReached_ShouldMarkAsFailed() throws Exception {
            int maxAttempts = notificationProperties.retry().maxAttempts();

            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("jobseeker-456")
                  .setRecipientEmail("applicant@acme.com")
                  .setType(NotificationType.APPLICATION_STATUS_UPDATED)
                  .setStatus(PENDING)
                  .setAttempts(maxAttempts)
                  .setSubject("Application Update")
                  .setTemplateName("welcome"));

            doThrow(new RuntimeException("SMTP Server Unavailable"))
                  .when(emailService).sendHtml(any());

            singleNotificationSender.attemptSend(notification.getId());

            Notification updated = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(FAILED);
            assertThat(updated.getLastError()).isEqualTo("SMTP Server Unavailable");
        }
    }

    @Nested
    class ExecuteSend {

        @Test
        void executeSend_ShouldSubmitTaskToExecutor() throws Exception {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("user-300")
                  .setRecipientEmail("async@acme.com")
                  .setType(NotificationType.APPLICATION_STATUS_UPDATED)
                  .setStatus(PENDING)
                  .setSubject("Async Test")
                  .setTemplateName("welcome"));

            CountDownLatch latch = new CountDownLatch(1);

            doAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                latch.countDown();
                return null;
            }).when(emailTaskExecutor).execute(any(Runnable.class));

            singleNotificationSender.executeSend(notification.getId());

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            Notification updated = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(SENT);
        }

        @Test
        void executeSend_WhenExecutorIsSaturated_ShouldKeepNotificationPending() {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("user-400")
                  .setStatus(PENDING)
                  .setSubject("Saturated Test")
                  .setTemplateName("welcome"));

            doThrow(new RejectedExecutionException("Queue full"))
                  .when(emailTaskExecutor).execute(any(Runnable.class));

            singleNotificationSender.executeSend(notification.getId());

            Notification dbState = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(dbState).isNotNull();
            assertThat(dbState.getStatus()).isEqualTo(PENDING);
            verifyNoInteractions(emailService);
        }
    }
}