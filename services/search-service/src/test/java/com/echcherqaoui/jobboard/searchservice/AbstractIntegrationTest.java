package com.echcherqaoui.jobboard.searchservice;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractIntegrationTest {

    protected static final ElasticsearchContainer ELASTICSEARCH =
          new ElasticsearchContainer(DockerImageName.parse("elasticsearch:8.18.8"))
                .withEnv("xpack.security.enabled", "false");

    static {
        ELASTICSEARCH.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
    }
}