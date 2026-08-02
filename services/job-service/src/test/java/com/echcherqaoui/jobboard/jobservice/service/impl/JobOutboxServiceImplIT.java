package com.echcherqaoui.jobboard.jobservice.service.impl;

import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.job.event.JobStatusChangedEvent;
import com.echcherqaoui.jobboard.jobservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.service.JobOutboxService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel.MID;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.OPEN;
import static com.echcherqaoui.jobboard.jobservice.model.JobType.FULL_TIME;
import static com.echcherqaoui.jobboard.jobservice.model.WorkModality.REMOTE;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class JobOutboxServiceImplIT extends AbstractIntegrationTest {

    @Autowired
    private JobOutboxService jobOutboxService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @MockitoBean
    private SignatureService signatureService;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();

        // Bypass Confluent's registry-dependent wire format entirely
        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });

        when(signatureService.sign(anyString(), anyString(), anyString())).thenReturn("test-signature");
    }

    private Job buildJob(String title) {
        return new Job()
              .setId(UUID.randomUUID())
              .setRecruiterId(UUID.randomUUID())
              .setTitle(title)
              .setDescription("desc")
              .setStatus(OPEN)
              .setWorkModality(REMOTE)
              .setJobType(FULL_TIME)
              .setExperienceLevel(MID)
              .setCreatedAt(OffsetDateTime.now(UTC));
    }

    @Nested
    @DisplayName("publishJobStatusChanged")
    class PublishJobStatusChanged {

        @Test
        void includesJobTitle() throws Exception {
            Job job = buildJob("Backend Engineer");

            jobOutboxService.publishJobStatusChanged(job);

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            JobStatusChangedEvent parsed = JobStatusChangedEvent.parseFrom(events.get(0).getPayload());
            assertThat(parsed.getJobTitle()).isEqualTo("Backend Engineer");
        }
    }

    @Nested
    @DisplayName("publishJobExpiredBatch")
    class PublishJobExpiredBatch {

        @Test
        void includesJobTitle_forComparison() throws Exception {
            Job job = buildJob("Software Engineer");

            jobOutboxService.publishJobExpiredBatch(List.of(job));

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            JobStatusChangedEvent parsed = JobStatusChangedEvent.parseFrom(events.get(0).getPayload());
            assertThat(parsed.getJobTitle()).isEqualTo("Software Engineer");
        }
    }
}