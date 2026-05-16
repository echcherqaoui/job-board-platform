package com.echcherqaoui.jobboard.authservice.config.props;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthServerProps.class)
public class SecurityPropertiesConfig {
}
