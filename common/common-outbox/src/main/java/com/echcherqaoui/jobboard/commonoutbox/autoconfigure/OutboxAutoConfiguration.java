package com.echcherqaoui.jobboard.commonoutbox.autoconfigure;

import com.echcherqaoui.jobboard.commonoutbox.partition.OutboxPartitionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnBean(DataSource.class)
@AutoConfigurationPackage(basePackages = "com.echcherqaoui.jobboard.commonoutbox")
public class OutboxAutoConfiguration {

    @Bean
    @DependsOn("flyway")
    @ConditionalOnProperty(name = "outbox.partition.enabled", havingValue = "true", matchIfMissing = true)
    public OutboxPartitionManager outboxPartitionManager(JdbcTemplate jdbcTemplate,
                                                         @Value("${outbox.partition.retention-days:7}") int retentionDays,
                                                         @Value("${outbox.partition.future-days:2}") int futureDays) {
        return new OutboxPartitionManager(
              jdbcTemplate,
              retentionDays,
              futureDays
        );
    }
}