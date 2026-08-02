package com.echcherqaoui.jobboard.userservice.kafka.consumer;

import com.echcherqaoui.jobboard.auth.event.RecruiterRegisteredEvent;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;
import com.echcherqaoui.jobboard.userservice.repository.RecruiterProfileRepository;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class UserEventsConsumerIT {

    static final Network network = Network.newNetwork();

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.0")
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
        KafkaTemplate<String, Message> kafkaTemplate(KafkaProperties kafkaProperties) {
            Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
            props.putAll(kafkaProperties.getProperties());

            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);

            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.properties.schema.registry.url",
              () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        registry.add("spring.kafka.properties.auto.register.schemas", () -> "true");
    }

    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    @Autowired
    private RecruiterProfileRepository recruiterProfileRepository;

    @Value("${kafka.topics.auth.user-events}")
    private String topic;

    @MockitoBean
    private SignatureService signatureService;

    @BeforeEach
    void setUp() {
        recruiterProfileRepository.deleteAll();
    }

    @Test
    void consume_ValidRecruiterRegisteredEvent_ShouldInitializeProfileInDatabase() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        RecruiterRegisteredEvent event = RecruiterRegisteredEvent.newBuilder()
              .setEventId(eventId)
              .setUserId(userId.toString())
              .setEmail("john.recruiter@acme.com")
              .setFirstName("John")
              .setLastName("Doe")
              .setSignature("valid-signature")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
              .build();

        // Act
        kafkaTemplate.send(topic, userId.toString(), event);

        // Assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            RecruiterProfile profile = recruiterProfileRepository.findById(userId).orElse(null);
            assertThat(profile).isNotNull();
            assertThat(profile.getEmail()).isEqualTo("john.recruiter@acme.com");
            assertThat(profile.getFirstName()).isEqualTo("John");
            assertThat(profile.getLastName()).isEqualTo("Doe");
            assertThat(profile.isOnboardingCompleted()).isFalse();
        });
    }

    @Test
    void consume_InvalidSignature_ShouldThrowExceptionAndNotPersist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        RecruiterRegisteredEvent event = RecruiterRegisteredEvent.newBuilder()
              .setEventId(UUID.randomUUID().toString())
              .setUserId(userId.toString())
              .setEmail("tampered@acme.com")
              .setSignature("invalid-sig")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build())
              .build();

        // Act
        kafkaTemplate.send(topic, userId.toString(), event);

        // Assert
        await().pollDelay(Duration.ofSeconds(2))
              .atMost(Duration.ofSeconds(5))
              .untilAsserted(() -> assertThat(recruiterProfileRepository.findById(userId)).isEmpty());
    }

    @Test
    void consume_DuplicateEvent_ShouldBeIdempotent() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        RecruiterRegisteredEvent event = RecruiterRegisteredEvent.newBuilder()
              .setEventId(UUID.randomUUID().toString())
              .setUserId(userId.toString())
              .setEmail("john.recruiter@acme.com")
              .setFirstName("John")
              .setLastName("Doe")
              .setSignature("valid-signature")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
              .build();

        // Send the same event twice
        kafkaTemplate.send(topic, userId.toString(), event);
        kafkaTemplate.send(topic, userId.toString(), event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(recruiterProfileRepository.count()).isEqualTo(1);
            RecruiterProfile profile = recruiterProfileRepository.findById(userId).orElse(null);
            assertThat(profile).isNotNull();
            assertThat(profile.getEmail()).isEqualTo("john.recruiter@acme.com");
        });
    }
}