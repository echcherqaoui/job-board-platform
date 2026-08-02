package com.echcherqaoui.jobboard.notificationservice.kafka.consumer;

import com.echcherqaoui.jobboard.auth.event.JobSeekerRegisteredEvent;
import com.echcherqaoui.jobboard.auth.event.RecruiterRegisteredEvent;
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
import org.springframework.context.annotation.Primary;
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
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class AuthEventConsumerIT {

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
        @Primary
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

    @Value("${kafka.topics.auth.auth-events}")
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
    class RecruiterRegistered {

        @Test
        void consume_ValidRecruiterRegisteredEvent_ShouldTriggerNotificationService() {
            String eventId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String email = "recruiter@acme.com";
            Instant now = Instant.now();

            when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

            RecruiterRegisteredEvent event = RecruiterRegisteredEvent.newBuilder()
                  .setEventId(eventId)
                  .setUserId(userId)
                  .setFirstName("Jane")
                  .setLastName("Doe")
                  .setEmail(email)
                  .setSignature("valid-signature")
                  .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                  .build();

            kafkaTemplate.send(topic, userId, event);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                  verify(notificationService).sendWelcome(
                        userId,
                        email,
                        "RECRUITER"
                  )
            );
        }

        @Test
        void consume_InvalidSignature_ShouldNotCallNotificationService() {
            String userId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

            RecruiterRegisteredEvent event = RecruiterRegisteredEvent.newBuilder()
                  .setEventId(UUID.randomUUID().toString())
                  .setUserId(userId)
                  .setFirstName("Jane")
                  .setLastName("Doe")
                  .setEmail("tampered@acme.com")
                  .setSignature("invalid-signature")
                  .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                  .build();

            kafkaTemplate.send(topic, userId, event);

            await().pollDelay(Duration.ofSeconds(2))
                  .atMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> {
                      verify(signatureService).verify(anyString(), anyString(), anyString(), anyString());
                      verify(notificationService, never()).sendWelcome(anyString(), anyString(), anyString());
                  });
        }
    }

    @Nested
    class JobSeekerRegistered {

        @Test
        void consume_ValidJobSeekerRegisteredEvent_ShouldTriggerNotificationService() {
            String eventId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();
            String email = "jobseeker@acme.com";
            Instant now = Instant.now();

            when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

            JobSeekerRegisteredEvent event = JobSeekerRegisteredEvent.newBuilder()
                  .setEventId(eventId)
                  .setUserId(userId)
                  .setFirstName("John")
                  .setLastName("Smith")
                  .setEmail(email)
                  .setSignature("valid-signature")
                  .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                  .build();

            kafkaTemplate.send(topic, userId, event);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                  verify(notificationService).sendWelcome(
                        userId,
                        email,
                        "JOBSEEKER"
                  )
            );
        }

        @Test
        void consume_InvalidSignature_ShouldNotCallNotificationService() {
            String userId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

            JobSeekerRegisteredEvent event = JobSeekerRegisteredEvent.newBuilder()
                  .setEventId(UUID.randomUUID().toString())
                  .setUserId(userId)
                  .setFirstName("John")
                  .setLastName("Smith")
                  .setEmail("tampered@acme.com")
                  .setSignature("invalid-signature")
                  .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                  .build();

            kafkaTemplate.send(topic, userId, event);

            await().pollDelay(Duration.ofSeconds(2))
                  .atMost(Duration.ofSeconds(5))
                  .untilAsserted(() -> {
                      verify(signatureService).verify(anyString(), anyString(), anyString(), anyString());
                      verify(notificationService, never()).sendWelcome(anyString(), anyString(), anyString());
                  });
        }
    }
}