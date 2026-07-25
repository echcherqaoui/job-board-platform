package com.echcherqaoui.jobboard.userservice.kafka.consumer;

import com.echcherqaoui.jobboard.sharedutils.kafka.AbstractEventConsumer;
import com.echcherqaoui.jobboard.userservice.kafka.handler.AuthHandler;
import com.google.protobuf.Message;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserEventsConsumer extends AbstractEventConsumer<AuthHandler> {

    public UserEventsConsumer(@NonNull List<AuthHandler> handlers) {
        super(handlers);
    }

    @KafkaListener(
          topics = "${kafka.topics.auth.user-events}",
          groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(@NonNull ConsumerRecord<String, Message> consumerRecord) {
        dispatch(consumerRecord);
    }
}

