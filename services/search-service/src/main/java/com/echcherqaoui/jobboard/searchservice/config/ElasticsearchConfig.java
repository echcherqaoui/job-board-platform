package com.echcherqaoui.jobboard.searchservice.config;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS;

/**
 * Forces dates to be saved as readable strings instead of raw numbers.
 * This fixes the Elasticsearch parsing error without changing date formats
 * in the rest of the application.
 */
@Configuration
public class ElasticsearchConfig {

    @Bean
    public JsonpMapper jsonpMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.createXmlMapper(false).build()
              .disable(WRITE_DATES_AS_TIMESTAMPS)
              .disable(WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);

        return new JacksonJsonpMapper(mapper);
    }
}