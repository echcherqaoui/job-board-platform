package com.echcherqaoui.jobboard.jobservice.config;

import com.echcherqaoui.jobboard.user.event.CompanyDeletedEvent;
import com.echcherqaoui.jobboard.user.event.CompanyUpsertedEvent;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.Map;

import static io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE;
import static org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    // ── DLT Producer ──────────────────────────────────────────────
    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate() {
        return new KafkaTemplate<>(
              new DefaultKafkaProducerFactory<>(
                    kafkaProperties.buildProducerProperties(null)
              )
        );
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
          KafkaTemplate<String, Object> dltKafkaTemplate) {
        return new DeadLetterPublishingRecoverer(dltKafkaTemplate,
              (consumerRecord, ex) -> new TopicPartition(consumerRecord.topic() + "-dlt", 0));
    }

    // ── Consumers ─────────────────────────────────────────────────
    private <T extends Message> ConsumerFactory<String, T> buildConsumerFactory(Class<T> protoType) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(SPECIFIC_PROTOBUF_VALUE_TYPE, protoType);

        return new DefaultKafkaConsumerFactory<>(
              props,
              new StringDeserializer(),
              new ErrorHandlingDeserializer<>(new KafkaProtobufDeserializer<>())
        );
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> buildFactory(ConsumerFactory<String, T> consumerFactory,
                                                                                DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.setConcurrency(3);
        factory.getContainerProperties()
              .setAckMode(MANUAL_IMMEDIATE);

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CompanyUpsertedEvent> companyUpsertedListenerContainerFactory(DefaultErrorHandler errorHandler) {
        return buildFactory(buildConsumerFactory(CompanyUpsertedEvent.class), errorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CompanyDeletedEvent> companyDeletedListenerContainerFactory(DefaultErrorHandler errorHandler) {
        return buildFactory(buildConsumerFactory(CompanyDeletedEvent.class), errorHandler);
    }
}