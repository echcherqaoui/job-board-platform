package com.echcherqaoui.jobboard.notificationservice.dto;

public record ApplicationNotificationContext(
      String eventId,
      String applicantId,
      String jobId,
      String jobTitle,
      String companyName,
      String newStatus,
      String note,
      String applicationId) {
}