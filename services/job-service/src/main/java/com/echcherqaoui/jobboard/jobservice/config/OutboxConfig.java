package com.echcherqaoui.jobboard.jobservice.config;

import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OutboxConfig {

    private final KafkaProperties kafkaProperties;

    @Bean
    public KafkaProtobufSerializer<Message> kafkaProtobufSerializer() {
        KafkaProtobufSerializer<Message> serializer = new KafkaProtobufSerializer<>();
        serializer.configure(
              kafkaProperties.buildProducerProperties(null),
              false
        );

        return serializer;
    }
}