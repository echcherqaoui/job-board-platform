package com.echcherqaoui.jobboard.searchservice.kafka.consumer;

import com.echcherqaoui.jobboard.job.event.JobDeletedEvent;
import com.echcherqaoui.jobboard.job.event.JobStatusChangedEvent;
import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;
import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import com.echcherqaoui.jobboard.searchservice.repository.JobDocumentRepository;
import com.echcherqaoui.jobboard.security.service.SignatureService;
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
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class JobEventConsumerIT {

    static final Network network = Network.newNetwork();

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
          DockerImageName.parse("elasticsearch:8.18.8"))
          .withEnv("xpack.security.enabled", "false");

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

            props.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);

            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.properties.schema.registry.url",
              () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        registry.add("spring.kafka.properties.auto.register.schemas", () -> "true");
        registry.add("kafka.topics.job.job-events", () -> "jobboard.events.job");
    }

    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    @Autowired
    private JobDocumentRepository jobDocumentRepository;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @MockitoBean
    private SignatureService signatureService;

    @BeforeEach
    void setUp() {
        IndexOperations indexOps = elasticsearchTemplate.indexOps(JobDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.create();
        indexOps.putMapping();

        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void shouldConsumeJobUpsertedEventAndIndexToElasticsearch() {
        String jobId = "job-e2e-100";
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.newBuilder()
              .setSeconds(now.getEpochSecond())
              .setNanos(now.getNano())
              .build();

        JobUpsertedEvent event = JobUpsertedEvent.newBuilder()
              .setEventId("evt-upsert-1")
              .setJobId(jobId)
              .setRecruiterId("recruiter-1")
              .setCompanyName("Acme Corp")
              .setCompanyLogo("https://cdn.example.com/logo.png")
              .setTitle("Staff Software Engineer")
              .setDescription("Backend infrastructure development.")
              .setRequirements("Kafka and Elasticsearch experience.")
              .setLocation("Remote")
              .setWorkModality("REMOTE")
              .setJobType("FULL_TIME")
              .setExperienceLevel("SENIOR")
              .setSalaryMinCents(12000000)
              .setSalaryMaxCents(16000000)
              .setCurrency("USD")
              .setStatus("ACTIVE")
              .addAllSkills(List.of("Java", "Kafka", "Elasticsearch"))
              .setCreatedAt(timestamp)
              .setExpiresAt(timestamp)
              .setOccurredAt(timestamp)
              .setSignature("valid-sig")
              .build();

        kafkaTemplate.send("jobboard.events.job", jobId, event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(jobDocumentRepository.findById(jobId)).isPresent();
            JobDocument doc = jobDocumentRepository.findById(jobId).get();
            assertThat(doc.getTitle()).isEqualTo("Staff Software Engineer");
            assertThat(doc.getCompanyName()).isEqualTo("Acme Corp");
            assertThat(doc.getStatus()).isEqualTo("ACTIVE");
            assertThat(doc.getSkills()).containsExactly("Java", "Kafka", "Elasticsearch");
        });
    }

    @Test
    void shouldThrowExceptionAndNotIndexWhenSignatureIsInvalid() {
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        String jobId = "job-tampered-200";
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(1_700_000_000L).build();

        JobUpsertedEvent event = JobUpsertedEvent.newBuilder()
              .setEventId("evt-tampered")
              .setJobId(jobId)
              .setRecruiterId("recruiter-1")
              .setTitle("Tampered Job Title")
              .setOccurredAt(timestamp)
              .setSignature("invalid-sig")
              .build();

        kafkaTemplate.send("jobboard.events.job", jobId, event);

        await().pollDelay(Duration.ofSeconds(2))
              .atMost(Duration.ofSeconds(5))
              .untilAsserted(() -> assertThat(jobDocumentRepository.findById(jobId)).isEmpty());
    }

    @Test
    void shouldConsumeJobStatusChangedEventAndUpdateStatusInElasticsearch() {
        String jobId = "job-status-300";
        JobDocument existingDocument = new JobDocument()
              .setId(jobId)
              .setRecruiterId("recruiter-1")
              .setCompanyName("Acme Corp")
              .setTitle("Backend Engineer")
              .setStatus("ACTIVE");

        jobDocumentRepository.save(existingDocument);

        Timestamp timestamp = Timestamp.newBuilder().setSeconds(1_700_000_000L).build();
        JobStatusChangedEvent event = JobStatusChangedEvent.newBuilder()
              .setEventId("evt-status-1")
              .setJobId(jobId)
              .setRecruiterId("recruiter-1")
              .setJobStatus("CLOSED")
              .setJobTitle("Backend Engineer")
              .setOccurredAt(timestamp)
              .setSignature("valid-sig")
              .build();

        kafkaTemplate.send("jobboard.events.job", jobId, event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JobDocument updated = jobDocumentRepository.findById(jobId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CLOSED");
            assertThat(updated.getTitle()).isEqualTo("Backend Engineer");
        });
    }

    @Test
    void shouldConsumeJobDeletedEventAndRemoveFromElasticsearch() {
        String jobId = "job-delete-400";
        JobDocument existingDocument = new JobDocument()
              .setId(jobId)
              .setRecruiterId("recruiter-1")
              .setCompanyName("Acme Corp")
              .setTitle("Cloud Architect")
              .setStatus("ACTIVE");

        jobDocumentRepository.save(existingDocument);
        assertThat(jobDocumentRepository.findById(jobId)).isPresent();

        Timestamp timestamp = Timestamp.newBuilder().setSeconds(1_700_000_000L).build();
        JobDeletedEvent event = JobDeletedEvent.newBuilder()
              .setEventId("evt-delete-1")
              .setJobId(jobId)
              .setRecruiterId("recruiter-1")
              .setOccurredAt(timestamp)
              .setSignature("valid-sig")
              .build();

        kafkaTemplate.send("jobboard.events.job", jobId, event);

        await().atMost(Duration.ofSeconds(10))
              .untilAsserted(() -> assertThat(jobDocumentRepository.findById(jobId)).isEmpty());
    }
}