package com.echcherqaoui.jobboard.userservice.storage;

import com.cloudinary.Cloudinary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(CloudinaryProperties props) {
        return new Cloudinary(Map.of(
              "cloud_name", props.cloudName(),
              "api_key", props.apiKey(),
              "api_secret", props.apiSecret(),
              "secure", true
        ));
    }
}