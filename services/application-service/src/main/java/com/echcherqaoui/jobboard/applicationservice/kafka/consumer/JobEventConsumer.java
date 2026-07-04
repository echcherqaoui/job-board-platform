package com.echcherqaoui.jobboard.applicationservice.kafka.consumer;

import com.echcherqaoui.jobboard.applicationservice.kafka.handler.JobEventHandler;
import com.echcherqaoui.jobboard.exception.core.EventProcessingException;
import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.echcherqaoui.jobboard.exception.core.CommonErrorCode.DESERIALIZATION_FAILED;

@Component
@Slf4j
public class JobEventConsumer {

    private final Map<String, JobEventHandler> handlerMap;

    public JobEventConsumer(@NonNull List<JobEventHandler> handlers) {
        this.handlerMap = handlers.stream()
              .collect(Collectors.toMap(JobEventHandler::getDescriptorFullName, eventHandler -> eventHandler));
    }

    @KafkaListener(
          topics = { "${kafka.topics.job.job-events}"},
          groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(@NonNull ConsumerRecord<String, Message> consumerRecord, @NonNull Acknowledgment ack) {
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

        JobEventHandler handler = handlerMap.get(schemaFullName);

        if (handler == null) {
            log.info(
                  "Skipping unhandled event type '{}' at offset: {}. This service does not monitor this lifecycle phase.",
                  schemaFullName,
                  consumerRecord.offset()
            );

            ack.acknowledge();

            return;
        }

        handler.handle(consumerRecord.value());

        log.info(
              "Successfully completed fan-out execution for record key: {} at offset: {}",
              consumerRecord.key(),
              consumerRecord.offset()
        );

        ack.acknowledge();
    }
}
