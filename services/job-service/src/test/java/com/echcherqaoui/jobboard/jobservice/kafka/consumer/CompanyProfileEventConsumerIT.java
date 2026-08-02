package com.echcherqaoui.jobboard.jobservice.kafka.consumer;

import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.repository.CompanyProfileRepository;
import com.echcherqaoui.jobboard.jobservice.repository.ProcessedEventRepository;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.user.event.CompanyDeletedEvent;
import com.echcherqaoui.jobboard.user.event.CompanyUpsertedEvent;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class CompanyProfileEventConsumerIT {

    static final Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.0");

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
          DockerImageName.parse("confluentinc/cp-kafka:7.7.7"))
          .withNetwork(network)
          .withNetworkAliases("kafka")
          // Expose internal listener for containers on the same Docker network
          .withListener("kafka:19092");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> schemaRegistry = new GenericContainer<>(
          DockerImageName.parse("confluentinc/cp-schema-registry:7.7.7"))
          .withExposedPorts(8081)
          .withNetwork(network)
          .withNetworkAliases("schema-registry")
          .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
          // Point to the internal listener registered on ConfluentKafkaContainer
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

            props.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);

            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.properties.schema.registry.url",
              () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        registry.add("spring.kafka.properties.auto.register.schemas", () -> "true");
        registry.add("kafka.topics.user.company-events", () -> "user-company-events-topic");
    }

    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private SignatureService signatureService;

    @BeforeEach
    void setUp() {
        companyProfileRepository.deleteAll();
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldConsumeFromKafkaAndPersistToPostgres() {
        UUID recruiterId = UUID.randomUUID();
        CompanyUpsertedEvent event = CompanyUpsertedEvent.newBuilder()
              .setEventId("evt-e2e")
              .setRecruiterId(recruiterId.toString())
              .setCompanyName("Acme Corp")
              .setCompanyLogo("logo.png")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(1_700_000_000L).build())
              .setSignature("sig")
              .build();

        kafkaTemplate.send("user-company-events-topic", recruiterId.toString(), event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(companyProfileRepository.findById(recruiterId)).isPresent();
            assertThat(companyProfileRepository.findById(recruiterId).get().getCompanyName())
                  .isEqualTo("Acme Corp");
        });
    }

    @Test
    void shouldThrowExceptionAndRollbackWhenSignatureIsInvalid() {
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        UUID recruiterId = UUID.randomUUID();
        CompanyUpsertedEvent event = CompanyUpsertedEvent.newBuilder()
              .setEventId("evt-tampered")
              .setRecruiterId(recruiterId.toString())
              .setCompanyName("Tampered Company")
              .setSignature("invalid-sig")
              .build();

        kafkaTemplate.send("user-company-events-topic", recruiterId.toString(), event);

        await().pollDelay(Duration.ofSeconds(2))
              .atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(companyProfileRepository.findById(recruiterId)).isEmpty());
    }

    @Test
    void shouldHandleCompanyDeletedEventAndRemoveFromDatabase() {
        UUID recruiterId = UUID.randomUUID();
        CompanyProfile existingProfile = new CompanyProfile()
              .setRecruiterId(recruiterId)
              .setCompanyName("Acme Corp")
              .setCompanyLogo("logo.png")
              .setLastEventId("")
              .setUpdatedAt(OffsetDateTime.now());

        companyProfileRepository.save(existingProfile);

        CompanyDeletedEvent event = CompanyDeletedEvent.newBuilder()
              .setEventId("evt-delete")
              .setRecruiterId(recruiterId.toString())
              .setSignature("sig")
              .build();

        kafkaTemplate.send("user-company-events-topic", recruiterId.toString(), event);

        await().atMost(Duration.ofSeconds(10))
              .untilAsserted(() -> assertThat(companyProfileRepository.findById(recruiterId)).isEmpty());
    }

    @Test
    void shouldProcessDuplicateEventIdempotently() {
        UUID recruiterId = UUID.randomUUID();
        String duplicateEventId = "evt-duplicate-123";

        CompanyUpsertedEvent firstEvent = CompanyUpsertedEvent.newBuilder()
              .setEventId(duplicateEventId)
              .setRecruiterId(recruiterId.toString())
              .setCompanyName("Original Corp")
              .setSignature("sig")
              .build();

        // 2nd event reuses the SAME eventId but changes the name
        CompanyUpsertedEvent duplicateEventWithNewData = CompanyUpsertedEvent.newBuilder()
              .setEventId(duplicateEventId)
              .setRecruiterId(recruiterId.toString())
              .setCompanyName("Should Be Ignored Corp")
              .setSignature("sig")
              .build();

        // Send first event
        kafkaTemplate.send("user-company-events-topic", recruiterId.toString(), firstEvent);

        // Wait until 1st event completes and hits DB
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(companyProfileRepository.findById(recruiterId)).isPresent();
            assertThat(companyProfileRepository.findById(recruiterId).get().getCompanyName())
                  .isEqualTo("Original Corp");
        });

        // Send 2nd duplicate event ID
        kafkaTemplate.send("user-company-events-topic", recruiterId.toString(), duplicateEventWithNewData);

        // Assert that:
        // The database name is STILL "Original Corp" (duplicate was ignored).
        // ProcessedEvent repository logged the eventId exactly once.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(companyProfileRepository.findById(recruiterId).get().getCompanyName())
                  .isEqualTo("Original Corp");
            assertThat(processedEventRepository.existsById(duplicateEventId)).isTrue();
        });
    }
}