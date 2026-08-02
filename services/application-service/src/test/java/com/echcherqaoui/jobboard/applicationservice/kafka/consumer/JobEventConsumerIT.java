package com.echcherqaoui.jobboard.applicationservice.kafka.consumer;

import com.echcherqaoui.jobboard.applicationservice.service.ApplicationDataAccess;
import com.echcherqaoui.jobboard.job.event.JobStatusChangedEvent;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
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

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class JobEventConsumerIT {

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

    @Value("${kafka.topics.job.job-events}")
    private String topic;

    @MockitoBean
    private SignatureService signatureService;

    @MockitoBean
    private ApplicationDataAccess applicationDataAccess;

    @Test
    void consume_ClosedJobStatusChangedEvent_ShouldExecuteBulkReject() {
        UUID jobId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String jobTitle = "Senior Java Engineer";
        Instant now = Instant.now();

        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        JobStatusChangedEvent event = JobStatusChangedEvent.newBuilder()
              .setEventId(eventId)
              .setJobId(jobId.toString())
              .setRecruiterId(UUID.randomUUID().toString())
              .setJobStatus("CLOSED")
              .setJobTitle(jobTitle)
              .setSignature("valid-sig")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
              .build();

        kafkaTemplate.send(topic, jobId.toString(), event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
              verify(applicationDataAccess).bulkRejectAndExecute(jobId, jobTitle)
        );
    }

    @Test
    void consume_NonClosedJobStatusChangedEvent_ShouldIgnoreEvent() {
        UUID jobId = UUID.randomUUID();

        JobStatusChangedEvent event = JobStatusChangedEvent.newBuilder()
              .setEventId(UUID.randomUUID().toString())
              .setJobId(jobId.toString())
              .setRecruiterId(UUID.randomUUID().toString())
              .setJobStatus("PUBLISHED")
              .setJobTitle("Software Architect")
              .setSignature("valid-sig")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build())
              .build();

        kafkaTemplate.send(topic, jobId.toString(), event);

        await().pollDelay(Duration.ofSeconds(2))
              .atMost(Duration.ofSeconds(5))
              .untilAsserted(() ->
                    verify(applicationDataAccess, never()).bulkRejectAndExecute(any(), any())
              );
    }

    @Test
    void consume_InvalidSignature_ShouldNotExecuteBulkReject() {
        UUID jobId = UUID.randomUUID();

        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        JobStatusChangedEvent event = JobStatusChangedEvent.newBuilder()
              .setEventId(UUID.randomUUID().toString())
              .setJobId(jobId.toString())
              .setRecruiterId(UUID.randomUUID().toString())
              .setJobStatus("CLOSED")
              .setJobTitle("Backend Lead")
              .setSignature("invalid-sig")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).build())
              .build();

        kafkaTemplate.send(topic, jobId.toString(), event);

        await().pollDelay(Duration.ofSeconds(2))
              .atMost(Duration.ofSeconds(5))
              .untilAsserted(() ->
                    verify(applicationDataAccess, never()).bulkRejectAndExecute(any(), any())
              );
    }
}