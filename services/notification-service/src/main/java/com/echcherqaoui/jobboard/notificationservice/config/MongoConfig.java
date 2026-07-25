package com.echcherqaoui.jobboard.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
              new OffsetDateTimeToDateConverter(),
              new DateToOffsetDateTimeConverter()
        ));
    }

    private static class OffsetDateTimeToDateConverter implements Converter<OffsetDateTime, Date> {
        @Override
        @NonNull
        public Date convert(@NonNull OffsetDateTime source) {
            return Date.from(source.toInstant());
        }
    }

    private static class DateToOffsetDateTimeConverter implements Converter<Date, OffsetDateTime> {
        @Override
        @NonNull
        public OffsetDateTime convert(@NonNull Date source) {
            return source.toInstant().atZone(ZoneOffset.UTC).toOffsetDateTime();
        }
    }
}