package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationType;
import com.echcherqaoui.jobboard.notificationservice.grpc.JobSeekerProfileClient;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.FAILED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.PENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@SpringBootTest
class BatchNotificationSenderImplIT extends AbstractIntegrationTest {

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
    private BatchNotificationSenderImpl batchNotificationSender;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private JobSeekerProfileClient jobSeekerProfileClient;

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
    class AttemptBatchSend {

        @Test
        void attemptBatchSend_WhenAllGrpcEmailsResolved_ShouldDeliverBatchAndCommitSuccessToMongo() {
            Notification n1 = mongoTemplate.save(new Notification()
                  .setRecipientId("js-1")
                  .setType(NotificationType.APPLICATIONS_CANCELED)
                  .setStatus(PENDING)
                  .setSubject("Position Canceled")
                  .setTemplateName("welcome"));

            Notification n2 = mongoTemplate.save(new Notification()
                  .setRecipientId("js-2")
                  .setType(NotificationType.APPLICATIONS_CANCELED)
                  .setStatus(PENDING)
                  .setSubject("Position Canceled")
                  .setTemplateName("welcome"));

            List<Notification> batch = List.of(n1, n2);

            when(jobSeekerProfileClient.getEmailsByUserIds(List.of("js-1", "js-2")))
                  .thenReturn(Map.of("js-1", "js1@acme.com", "js-2", "js2@acme.com"));

            batchNotificationSender.attemptBatchSend(batch, "job-999");

            List<Notification> updatedList = mongoTemplate.findAll(Notification.class);
            assertThat(updatedList)
                  .extracting(Notification::getStatus)
                  .containsExactlyInAnyOrder(SENT, SENT);

            assertThat(updatedList)
                  .extracting(Notification::getRecipientEmail)
                  .containsExactlyInAnyOrder("js1@acme.com", "js2@acme.com");

            assertThat(greenMail.getReceivedMessages()).hasSize(2);
        }

        @Test
        void attemptBatchSend_WhenGrpcReturnsEmptyMap_ShouldHandleBatchFailureAndSetPendingOrFailed() {
            Notification n1 = mongoTemplate.save(new Notification()
                  .setRecipientId("js-1")
                  .setType(NotificationType.APPLICATIONS_CANCELED)
                  .setStatus(PENDING)
                  .setAttempts(4) // Max is 5; attemptBatchSend increments attempts in memory to 3 before processing
                  .setSubject("Position Canceled")
                  .setTemplateName("welcome"));

            when(jobSeekerProfileClient.getEmailsByUserIds(anyList()))
                  .thenReturn(Collections.emptyMap());

            batchNotificationSender.attemptBatchSend(List.of(n1), "job-999");

            Notification updated = mongoTemplate.findById(n1.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(FAILED);
            assertThat(updated.getLastError()).isEqualTo("gRPC email resolution returned empty map");
        }
    }
}