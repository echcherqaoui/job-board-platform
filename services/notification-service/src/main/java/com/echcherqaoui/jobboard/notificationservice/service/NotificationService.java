package com.echcherqaoui.jobboard.notificationservice.service;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationType;
import com.echcherqaoui.jobboard.notificationservice.dto.ApplicationNotificationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.APPLICATIONS_CANCELED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.APPLICATION_RECEIVED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.APPLICATION_STATUS_UPDATED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.WELCOME;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final NotificationOrchestrator orchestrator;

    @NonNull
    private String buildStatusSubject(@NonNull String status, String jobTitle) {
        return switch (status.toUpperCase()) {
            case "REVIEWED" -> String.format("Your application for %s has been reviewed", jobTitle);
            case "ACCEPTED" -> String.format("Congratulations! Your application for %s was accepted", jobTitle);
            default -> {
                log.warn("Unrecognized application status: {}", status);
                yield String.format("Update on your application for %s", jobTitle);
            }
        };
    }

    @NonNull
    private String buildIdempotencyKey(String eventId, @NonNull NotificationType type) {
        return eventId + ":" + type.name();
    }

    @NonNull
    private String buildIdempotencyKey(String eventId, String applicantId, @NonNull NotificationType type) {
        return eventId + ":" + applicantId + ":" + type.name();
    }

    public void sendWelcome(String userId,
                            String email,
                            @NonNull String role) {
        String message = String.format("Welcome email for %s", role);

        Notification notification = new Notification()
              .setRecipientId(userId)
              .setRecipientEmail(email)
              .setType(WELCOME)
              .setSubject("Welcome to Job Board Platform!")
              .setMessage(message)
              .setTemplateName("welcome")
              .setTemplateVars(Map.of("role", role.toLowerCase().replace("_", " ")));

        orchestrator.submit(notification);
    }

    public void sendApplicationReceived(String eventId,
                                        String recruiterId,
                                        String applicantName,
                                        String jobTitle,
                                        String applicationId,
                                        String jobId) {
        Map<String, Object> vars = Map.of(
              "applicantName", applicantName,
              "jobTitle", jobTitle,
              "applicationId", applicationId,
              "jobId", jobId
        );

        String subject = String.format("New application received for: %s", jobTitle);
        String message = String.format(" %s applied for %s", applicantName, jobTitle);

        Notification notification = new Notification()
              .setId(buildIdempotencyKey(eventId, APPLICATION_RECEIVED))
              .setRecipientId(recruiterId)
              .setType(APPLICATION_RECEIVED)
              .setSubject(subject)
              .setMessage(message)
              .setJobId(jobId)
              .setTemplateName("application-received")
              .setTemplateVars(vars);

        orchestrator.submit(notification);
    }

    public void sendApplicationStatusUpdated(@NonNull ApplicationNotificationContext context) {

        Map<String, Object> vars = Map.of(
              "jobTitle", context.jobTitle(),
              "companyName", context.companyName(),
              "newStatus", context.newStatus(),
              "note", context.note() != null ? context.note() : "",
              "applicationId", context.applicationId()
        );

        String subject = buildStatusSubject(context.newStatus(), context.jobTitle());
        String message = String.format("Your application for %s is now %s", context.jobTitle(), context.newStatus());

        Notification notification = new Notification()
              .setId(buildIdempotencyKey(context.eventId(), APPLICATION_STATUS_UPDATED))
              .setRecipientId(context.applicantId())
              .setType(APPLICATION_STATUS_UPDATED)
              .setSubject(subject)
              .setMessage(message)
              .setJobId(context.jobId())
              .setTemplateName("application-status-updated")
              .setTemplateVars(vars);

        orchestrator.submit(notification);
    }

    public void sendApplicationsCanceled(String eventId,
                                         String jobId,
                                         String jobTitle,
                                         List<String> applicantIds) {
        if (applicantIds == null || applicantIds.isEmpty())
            return;

        String subject =  String.format("Your application for %s has been cancelled", jobTitle);
        String message = String.format("Application cancelled for job: %s", jobTitle);

        Map<String, Object> vars = Map.of(
              "jobTitle", jobTitle,
              "newStatus", "CANCELLED",
              "note", "The job opening has been closed by the recruiter."
        );

        List<Notification> notifications = applicantIds.stream().map(
              applicantId ->
                    new Notification()
                          .setId(buildIdempotencyKey(eventId, applicantId, APPLICATIONS_CANCELED))
                          .setRecipientId(applicantId)
                          .setMessage(message)
                          .setJobId(jobId)
                          .setType(APPLICATIONS_CANCELED)
                          .setSubject(subject)
                          .setTemplateName("application-status-updated")
                          .setTemplateVars(vars)
        ).toList();

        orchestrator.submitBatch(notifications, jobId);
    }
}
