package com.atlasia.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ModelTierProperties.class)
public class ModelTierConfiguration {
}
