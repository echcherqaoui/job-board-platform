package com.echcherqaoui.jobboard.userservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

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
              (consumerRecord, ex) -> new TopicPartition(consumerRecord.topic() + "-dlt", -1));
    }
}