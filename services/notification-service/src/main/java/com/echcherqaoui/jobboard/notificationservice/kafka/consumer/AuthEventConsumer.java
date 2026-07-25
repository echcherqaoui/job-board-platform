package com.echcherqaoui.jobboard.notificationservice.kafka.consumer;

import com.echcherqaoui.jobboard.notificationservice.kafka.handler.AuthHandler;
import com.echcherqaoui.jobboard.sharedutils.kafka.AbstractEventConsumer;
import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class AuthEventConsumer extends AbstractEventConsumer<AuthHandler> {

    public AuthEventConsumer(@NonNull List<AuthHandler> handlers) {
        super(handlers);
    }

    @KafkaListener(
          topics = "${kafka.topics.auth.auth-events}",
          groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(@NonNull ConsumerRecord<String, Message> consumerRecord,
                        @NonNull Acknowledgment ack) {
        log.debug(
              "Consuming record from topic: {}, partition: {}, offset: {}, key: {}",
              consumerRecord.topic(),
              consumerRecord.partition(),
              consumerRecord.offset(),
              consumerRecord.key()
        );

        dispatch(consumerRecord);
        ack.acknowledge();
    }
}
