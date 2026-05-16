package com.echcherqaoui.jobboard.bffservice.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bff")
public record BffProps(String postLogoutRedirectUri) {
}