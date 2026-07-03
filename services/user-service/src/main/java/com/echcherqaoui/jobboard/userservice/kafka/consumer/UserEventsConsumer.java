package com.echcherqaoui.jobboard.userservice.kafka.consumer;

import com.echcherqaoui.jobboard.exception.core.EventProcessingException;
import com.echcherqaoui.jobboard.userservice.kafka.handler.AuthEventHandler;
import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.echcherqaoui.jobboard.exception.core.CommonErrorCode.DESERIALIZATION_FAILED;
import static com.echcherqaoui.jobboard.exception.core.CommonErrorCode.NO_HANDLER_FOUND;

@Slf4j
@Component
public class UserEventsConsumer {

    private final Map<String, AuthEventHandler> handlerMap;

    public UserEventsConsumer(@NonNull List<AuthEventHandler> handlers) {
        this.handlerMap = handlers.stream()
              .collect(Collectors.toMap(AuthEventHandler::getDescriptorFullName, eventHandler -> eventHandler));
    }

    @KafkaListener(
          topics = "${kafka.topics.auth.user-events}",
          groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(@NonNull ConsumerRecord<String, Message> consumerRecord) {
        Message payload = consumerRecord.value();

        if (payload == null) {
            log.error(
                  "Deserialization failed. Received null payload on topic: {}, partition: {}, offset: {}",
                  consumerRecord.topic(),
                  consumerRecord.partition(),
                  consumerRecord.offset()
            );

            throw new EventProcessingException(DESERIALIZATION_FAILED, consumerRecord.offset());
        }

        String schemaFullName = payload.getDescriptorForType().getFullName();

        AuthEventHandler handler = handlerMap.get(schemaFullName);

        if (handler == null) {
            log.error(
                  "Routing failed. No registered handler found matching schema descriptor '{}' at offset: {}",
                  schemaFullName,
                  consumerRecord.offset()
            );

            throw new EventProcessingException(NO_HANDLER_FOUND, schemaFullName, consumerRecord.offset());
        }

        handler.handle(consumerRecord.value());

        log.info(
              "Successfully completed fan-out execution for record key: {} at offset: {}",
              consumerRecord.key(),
              consumerRecord.offset()
        );
    }
}

