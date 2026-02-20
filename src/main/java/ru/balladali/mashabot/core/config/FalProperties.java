package ru.balladali.mashabot.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clients.fal")
public record FalProperties(
        String baseUrl,
        String modelPath,
        Integer timeoutSec
) {}
