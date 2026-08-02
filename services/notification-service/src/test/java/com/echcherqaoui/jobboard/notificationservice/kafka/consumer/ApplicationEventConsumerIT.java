package com.echcherqaoui.jobboard.notificationservice.kafka.consumer;

import com.echcherqaoui.jobboard.application.event.ApplicationStatusChangedEvent;
import com.echcherqaoui.jobboard.application.event.ApplicationSubmittedEvent;
import com.echcherqaoui.jobboard.application.event.JobApplicationsCanceledEvent;
import com.echcherqaoui.jobboard.notificationservice.dto.ApplicationNotificationContext;
import com.echcherqaoui.jobboard.notificationservice.repository.NotificationRepository;
import com.echcherqaoui.jobboard.notificationservice.service.NotificationService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class ApplicationEventConsumerIT {

    static final Network network = Network.newNetwork();

    @SuppressWarnings("resource")
    @Container
    static MongoDBContainer MONGO_DB = new MongoDBContainer("mongo:7.0")
          .withNetwork(network);

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
          DockerImageName.parse("confluentinc/cp-kafka:7.7.7"))
          .withNetwork(network)
          .withNetworkAliases("kafka")
          .withListener("kafka:19092");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> schemaRegistry = new GenericContainer<>(
          DockerImageName.parse("confluentinc/cp-schema-registry:7.7.7"))
          .withExposedPorts(8081)
          .withNetwork(network)
          .withNetworkAliases("schema-registry")
          .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
          .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
          .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
          .waitingFor(Wait.forHttp("/subjects").forStatusCode(200))
          .dependsOn(kafka);

    @TestConfiguration
    static class KafkaTemplateTestConfig {
        @Bean
        public KafkaTemplate<String, Message> kafkaTemplate(KafkaProperties kafkaProperties) {
            Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
            props.putAll(kafkaProperties.getProperties());

            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);

            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_DB::getReplicaSetUrl);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.properties.schema.registry.url",
              () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        registry.add("spring.kafka.properties.auto.register.schemas", () -> "true");
    }

    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Value("${kafka.topics.application.application-events}")
    private String topic;

    @MockitoBean
    private SignatureService signatureService;

    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        Mockito.reset(signatureService, notificationService);
    }

    @Nested
    class ApplicationSubmitted {

        @Test
        void consume_ValidApplicationSubmittedEvent_ShouldTriggerNotificationService() {
            String eventId = UUID.randomUUID().toString();
            String applicationId = UUID.randomUUID().toString();
            String recruiterId = UUID.randomUUID().toString();
            String jobId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

            ApplicationSubmittedEvent event = ApplicationSubmittedEvent.newBuilder()
                  .setEventId(eventId)
                  .setApplicationId(applicationId)
                  .setRecruiterId(recruiterId)
                  .setApplicantName("Jane Doe")
                  .setJobTitle("Software Engineer")
                  .setJobId(jobId)
                  .setSignature("valid-signature")
                  .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                  .build();

            kafkaTemplate.send(topic, applicationId, event);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                  verify(notificationService).sendApplicationReceived(
                        eventId,
                        recruiterId,
                        "Jane Doe",
                        "Software Engineer",
                        applicationId,
                        jobId
                  )
            );
        }

        @Test
        void consume_InvalidSignature_ShouldThrowSecurityExceptionAndNotCallService() {
            String applicationId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

            ApplicationSubmittedEvent event = ApplicationSubmittedEvent.newBuilder()
                  .setEventId(UUID.randomUUID().toString())
                  .setApplicationId(applicationId)
                  .setRecruiterId(UUID.randomUUID().toString())
                  .setApplicantName("Jane Doe")
                  .setJobTitle("Software Engineer")
                  .setJobId(UUID.randomUUID().toString())
                  .setSignature("invalid-signature")
                  .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                  .build();

            kafkaTemplate.send(topic, applicationId, event);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                // Verify that signature verification was actually performed
                verify(signatureService).verify(anyString(), anyString(), anyString(), anyString());

                //  Verify notification service was never invoked due to the exception
                verify(notificationService, never()).sendApplicationReceived(
                      anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
                );
            });
        }
    }

    @Nested
    class ApplicationStatusChanged {

        @Test
        void consume_ValidStatusChangedEvent_ShouldTriggerNotificationService() {
            String eventId = UUID.randomUUID().toString();
            String applicationId = UUID.randomUUID().toString();
            String applicantId = UUID.randomUUID().toString();
            String jobId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

            ApplicationStatusChangedEvent event = ApplicationStatusChangedEvent.newBuilder()
                  .setEventId(eventId)
                  .setApplicationId(applicationId)
                  .setApplicantId(applicantId)
                  .setJobId(jobId)
                  .setJobTitle("Frontend Engineer")
                  .setCompanyName("AgileCorp")
                  .setNewStatus("REVIEWED")
                  .setNote("Moving to interview step")
                  .setSignature("valid-signature")
                  .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                  .build();

            kafkaTemplate.send(topic, applicationId, event);

            ApplicationNotificationContext expectedContext = new ApplicationNotificationContext(
                  eventId,
                  applicantId,
                  jobId,
                  "Frontend Engineer",
                  "AgileCorp",
                  "REVIEWED",
                  "Moving to interview step",
                  applicationId
            );

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                  verify(notificationService).sendApplicationStatusUpdated(expectedContext)
            );
        }
    }

    @Nested
    class JobApplicationsCanceled {

        @Test
        void consume_ValidJobApplicationsCanceledEvent_ShouldTriggerNotificationService() {
            String eventId = UUID.randomUUID().toString();
            String jobId = UUID.randomUUID().toString();
            List<String> applicantIds = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
            Instant now = Instant.now();

            when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

            JobApplicationsCanceledEvent event = JobApplicationsCanceledEvent.newBuilder()
                  .setEventId(eventId)
                  .setJobId(jobId)
                  .setJobTitle("Backend Engineer")
                  .addAllApplicantIds(applicantIds)
                  .setSignature("valid-signature")
                  .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                  .build();

            kafkaTemplate.send(topic, jobId, event);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                  verify(notificationService).sendApplicationsCanceled(
                        eventId,
                        jobId,
                        "Backend Engineer",
                        applicantIds
                  )
            );
        }
    }
}