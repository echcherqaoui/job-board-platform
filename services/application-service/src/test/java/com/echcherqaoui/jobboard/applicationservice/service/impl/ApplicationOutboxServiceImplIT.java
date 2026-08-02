package com.echcherqaoui.jobboard.applicationservice.service.impl;

import com.echcherqaoui.jobboard.application.event.ApplicationStatusChangedEvent;
import com.echcherqaoui.jobboard.application.event.ApplicationSubmittedEvent;
import com.echcherqaoui.jobboard.application.event.JobApplicationsCanceledEvent;
import com.echcherqaoui.jobboard.applicationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class ApplicationOutboxServiceImplIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationOutboxServiceImpl applicationOutboxService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private SignatureService signatureService;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @MockitoBean
    private JwtContextHolder jwtContextHolder;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();

        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });
    }

    @Nested
    class PublishApplicationSubmitted {

        @Test
        void publishApplicationSubmitted_ShouldPersistOutboxEventWithValidProtobufAndSignature() throws InvalidProtocolBufferException {
            UUID applicationId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            UUID applicantId = UUID.randomUUID();
            String recruiterId = UUID.randomUUID().toString();
            OffsetDateTime now = OffsetDateTime.now();

            when(jwtContextHolder.getFullName()).thenReturn("John Doe");

            Application application = new Application()
                  .setId(applicationId)
                  .setJobId(jobId)
                  .setApplicantId(applicantId)
                  .setCvUrl("https://storage.com/cv.pdf")
                  .setStatus(PENDING)
                  .setSubmittedAt(now)
                  .setUpdatedAt(now);

            JobSummary job = JobSummary.newBuilder()
                  .setJobId(jobId.toString())
                  .setTitle("Senior Java Developer")
                  .setRecruiterId(recruiterId)
                  .setCompanyName("Acme Corp")
                  .build();

            applicationOutboxService.publishApplicationSubmitted(application, job);

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent event = events.get(0);
            assertThat(event.getAggregateType()).isEqualTo("application");
            assertThat(event.getAggregateId()).isEqualTo(applicationId.toString());
            assertThat(event.getEventType()).isEqualTo("application-submitted");
            assertThat(event.getCreatedAt()).isNotNull();

            ApplicationSubmittedEvent proto = ApplicationSubmittedEvent.parseFrom(event.getPayload());
            assertThat(proto.getApplicationId()).isEqualTo(applicationId.toString());
            assertThat(proto.getJobId()).isEqualTo(jobId.toString());
            assertThat(proto.getJobTitle()).isEqualTo("Senior Java Developer");
            assertThat(proto.getRecruiterId()).isEqualTo(recruiterId);
            assertThat(proto.getApplicantName()).isEqualTo("John Doe");
            assertThat(proto.getApplicantId()).isEqualTo(applicantId.toString());

            boolean isValidSignature = signatureService.verify(
                  proto.getEventId(),
                  proto.getApplicationId(),
                  String.valueOf(proto.getOccurredAt().getSeconds()),
                  proto.getSignature()
            );
            assertThat(isValidSignature).isTrue();
        }

        @Test
        void publishApplicationSubmitted_WhenApplicationIsNull_ShouldThrowNullPointerException() {
            JobSummary job = JobSummary.newBuilder().build();

            assertThatThrownBy(() -> applicationOutboxService.publishApplicationSubmitted(null, job))
                  .isInstanceOf(NullPointerException.class);

            assertThat(outboxEventRepository.findAll()).isEmpty();
        }

        @Test
        void publishApplicationSubmitted_WhenJobIsNull_ShouldThrowNullPointerException() {
            Application application = new Application().setId(UUID.randomUUID());

            assertThatThrownBy(() -> applicationOutboxService.publishApplicationSubmitted(application, null))
                  .isInstanceOf(NullPointerException.class);

            assertThat(outboxEventRepository.findAll()).isEmpty();
        }
    }

    @Nested
    class PublishApplicationStatusUpdated {

        @Test
        void publishApplicationStatusUpdated_ShouldPersistOutboxEventWithValidProtobufAndSignature() throws InvalidProtocolBufferException {
            UUID applicationId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            UUID applicantId = UUID.randomUUID();
            UUID callerId = UUID.randomUUID();

            Application application = new Application()
                  .setId(applicationId)
                  .setJobId(jobId)
                  .setApplicantId(applicantId)
                  .setStatus(ApplicationStatus.REVIEWED);

            JobSummary jobSummary = JobSummary.newBuilder()
                  .setJobId(jobId.toString())
                  .setTitle("Software Engineer")
                  .setCompanyName("Tech Corp")
                  .build();

            applicationOutboxService.publishApplicationStatusUpdated(
                  application,
                  PENDING,
                  ApplicationStatus.REVIEWED,
                  jobSummary,
                  callerId,
                  "Moving to review stage"
            );

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent event = events.get(0);
            assertThat(event.getAggregateType()).isEqualTo("application");
            assertThat(event.getAggregateId()).isEqualTo(applicationId.toString());
            assertThat(event.getEventType()).isEqualTo("application-status-changed");

            ApplicationStatusChangedEvent proto = ApplicationStatusChangedEvent.parseFrom(event.getPayload());
            assertThat(proto.getApplicationId()).isEqualTo(applicationId.toString());
            assertThat(proto.getApplicantId()).isEqualTo(applicantId.toString());
            assertThat(proto.getOldStatus()).isEqualTo("PENDING");
            assertThat(proto.getNewStatus()).isEqualTo("REVIEWED");
            assertThat(proto.getJobTitle()).isEqualTo("Software Engineer");
            assertThat(proto.getNote()).isEqualTo("Moving to review stage");
            assertThat(proto.getJobId()).isEqualTo(jobId.toString());
            assertThat(proto.getChangedBy()).isEqualTo(callerId.toString());
            assertThat(proto.getCompanyName()).isEqualTo("Tech Corp");

            boolean isValidSignature = signatureService.verify(
                  proto.getEventId(),
                  proto.getApplicationId(),
                  String.valueOf(proto.getOccurredAt().getSeconds()),
                  proto.getSignature()
            );
            assertThat(isValidSignature).isTrue();
        }

        @Test
        void publishApplicationStatusUpdated_WhenNoteIsNull_ShouldDefaultToEmptyStringInProto() throws InvalidProtocolBufferException {
            UUID applicationId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            UUID applicantId = UUID.randomUUID();
            UUID callerId = UUID.randomUUID();

            Application application = new Application()
                  .setId(applicationId)
                  .setJobId(jobId)
                  .setApplicantId(applicantId)
                  .setStatus(ApplicationStatus.ACCEPTED);

            JobSummary jobSummary = JobSummary.newBuilder()
                  .setJobId(jobId.toString())
                  .setTitle("Backend Engineer")
                  .setCompanyName("Startup Inc")
                  .build();

            applicationOutboxService.publishApplicationStatusUpdated(
                  application,
                  ApplicationStatus.REVIEWED,
                  ApplicationStatus.ACCEPTED,
                  jobSummary,
                  callerId,
                  null
            );

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            ApplicationStatusChangedEvent proto = ApplicationStatusChangedEvent.parseFrom(events.get(0).getPayload());
            assertThat(proto.getNote()).isEmpty();
        }

        @Test
        void publishApplicationStatusUpdated_WhenApplicationIsNull_ShouldThrowNullPointerException() {
            UUID callerId = UUID.randomUUID();
            JobSummary jobSummary = JobSummary.newBuilder().build();

            Executable action = () ->
                  applicationOutboxService.publishApplicationStatusUpdated(
                        null,
                        ApplicationStatus.PENDING,
                        ApplicationStatus.REVIEWED,
                        jobSummary,
                        callerId,
                        "Note"
                  );

            assertThatThrownBy(action::execute)
                  .isInstanceOf(NullPointerException.class);

            assertThat(outboxEventRepository.findAll()).isEmpty();
        }
    }

    @Nested
    class PublishJobApplicationsCanceled {

        @Test
        void publishJobApplicationsCanceled_ShouldPersistCanceledOutboxEventWithValidSignature() throws InvalidProtocolBufferException {
            UUID jobId = UUID.randomUUID();
            String jobTitle = "Full Stack Developer";
            List<String> applicantIds = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());

            applicationOutboxService.publishJobApplicationsCanceled(jobId, jobTitle, applicantIds);

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent event = events.get(0);
            assertThat(event.getAggregateType()).isEqualTo("application");
            assertThat(event.getAggregateId()).isEqualTo(jobId.toString());
            assertThat(event.getEventType()).isEqualTo("application-canceled");

            JobApplicationsCanceledEvent proto = JobApplicationsCanceledEvent.parseFrom(event.getPayload());
            assertThat(proto.getJobId()).isEqualTo(jobId.toString());
            assertThat(proto.getJobTitle()).isEqualTo(jobTitle);
            assertThat(proto.getApplicantIdsList()).containsExactlyElementsOf(applicantIds);

            boolean isValidSignature = signatureService.verify(
                  proto.getEventId(),
                  proto.getJobId(),
                  String.valueOf(proto.getOccurredAt().getSeconds()),
                  proto.getSignature()
            );
            assertThat(isValidSignature).isTrue();
        }

        @Test
        void publishJobApplicationsCanceled_WhenJobIdIsNull_ShouldThrowNullPointerException() {
            Executable action =
                  () -> applicationOutboxService.publishJobApplicationsCanceled(null, "Title", List.of());

            assertThatThrownBy(action::execute)
                  .isInstanceOf(NullPointerException.class);

            assertThat(outboxEventRepository.findAll()).isEmpty();
        }
    }
}