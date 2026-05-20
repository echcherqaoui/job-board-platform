package com.echcherqaoui.jobboard.authservice.config.kafka;

import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    // ── Producers ─────────────────────────────────────────────────
    @Bean
    public KafkaProtobufSerializer<Message> kafkaProtobufSerializer() {
        KafkaProtobufSerializer<Message> serializer = new KafkaProtobufSerializer<>();

        // Use the properties from application.yml (schema.registry.url, etc.)
        serializer.configure(
              kafkaProperties.buildProducerProperties(null),
              false
        );

        return serializer;
    }

}