package com.echcherqaoui.jobboard.userservice.config;

import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@Slf4j
public class KafkaRetryConfig {

    private static DefaultErrorHandler getHandler(DeadLetterPublishingRecoverer recoverer) {
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(4_000L);
        backOff.setMaxElapsedTime(30_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        errorHandler.addNotRetryableExceptions(
              EventSecurityException.class,
              DataIntegrityViolationException.class,
              DeserializationException.class
        );
        return errorHandler;
    }


    @Bean
    public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer recoverer) {
        DefaultErrorHandler handler = getHandler(recoverer);

        handler.setLogLevel(KafkaException.Level.ERROR);

        handler.setRetryListeners((consumerRecord, failure, deliveryAttempt) ->
              log.warn(
                    "Retry attempt {} for record on topic: {} partition: {} offset: {}",
                    deliveryAttempt,
                    consumerRecord.topic(),
                    consumerRecord.partition(),
                    consumerRecord.offset(),
                    failure
              )
        );

        return handler;
    }
}