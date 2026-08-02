package com.echcherqaoui.jobboard.notificationservice;

import com.echcherqaoui.jobboard.notificationservice.config.MongoConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Import(MongoConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final MongoDBContainer MONGO_DB =
          new MongoDBContainer("mongo:7.0");

    static {
        MONGO_DB.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_DB::getReplicaSetUrl);
    }
}