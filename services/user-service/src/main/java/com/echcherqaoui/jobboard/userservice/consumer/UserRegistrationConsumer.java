package com.echcherqaoui.jobboard.userservice.consumer;

import com.echcherqaoui.jobboard.auth.event.UserCreatedEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.userservice.service.JobSeekerProfileService;
import com.echcherqaoui.jobboard.userservice.service.RecruiterProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegistrationConsumer {

    private final SignatureService signatureService;
    private final JobSeekerProfileService jobSeekerService;
    private final RecruiterProfileService recruiterService;

    private void validateEvent(@NonNull UserCreatedEvent event) {
        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getUserId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid)
            throw new EventSecurityException(event.getEventId());
    }

    @KafkaListener(
          topics = "${kafka.topics.auth.job-seeker-registered}",
          groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeJobSeeker(UserCreatedEvent event) {
        validateEvent(event);

        jobSeekerService.initializeProfile(
              UUID.fromString(event.getUserId()),
              event.getEmail(),
              event.getFirstName(),
              event.getLastName()
        );

        log.info("Successfully initialized JobSeeker profile for user: {}", event.getUserId());
    }

    @KafkaListener(
          topics = "${kafka.topics.auth.recruiter-registered}",
          groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeRecruiter(UserCreatedEvent event) {
        validateEvent(event);

        recruiterService.initializeRecruiter(
              UUID.fromString(event.getUserId()),
              event.getEmail(),
              event.getFirstName(),
              event.getLastName()
        );

        log.info("Successfully initialized Recruiter profile for user: {}", event.getUserId());
    }
}