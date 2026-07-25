package com.echcherqaoui.jobboard.sharedutils.kafka;

import com.echcherqaoui.jobboard.exception.core.EventProcessingException;
import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.echcherqaoui.jobboard.exception.core.CommonErrorCode.DESERIALIZATION_FAILED;
import static com.echcherqaoui.jobboard.exception.core.CommonErrorCode.NO_HANDLER_FOUND;

@Slf4j
public abstract class AbstractEventConsumer <H extends EventHandler> {

    private final Map<String, H> handlerMap;

    protected AbstractEventConsumer(@NonNull List<H> handlers) {
        this.handlerMap = handlers.stream()
              .collect(Collectors.toMap(EventHandler::getDescriptorFullName, handler -> handler));
    }

    protected void dispatch(@NonNull ConsumerRecord<String, Message> consumerRecord) {
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
        EventHandler handler = handlerMap.get(schemaFullName);

        if (handler == null) {
            log.error(
                  "Routing failed. No registered handler found matching schema descriptor '{}' at offset: {}",
                  schemaFullName,
                  consumerRecord.offset()
            );
            throw new EventProcessingException(NO_HANDLER_FOUND, schemaFullName, consumerRecord.offset());
        }

        handler.handle(payload);
    }
}