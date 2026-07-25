package com.echcherqaoui.jobboard.jobservice.kafka.consumer;

import com.echcherqaoui.jobboard.jobservice.kafka.handler.UserHandler;
import com.echcherqaoui.jobboard.sharedutils.kafka.AbstractEventConsumer;
import com.google.protobuf.Message;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompanyProfileEventConsumer extends AbstractEventConsumer<UserHandler> {

    public CompanyProfileEventConsumer(@NonNull List<UserHandler> handlers) {
        super(handlers);
    }

    @KafkaListener(
          topics = "${kafka.topics.user.company-events}",
          groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(@NonNull ConsumerRecord<String, Message> consumerRecord, @NonNull Acknowledgment ack) {
        dispatch(consumerRecord);

        ack.acknowledge();
    }
}