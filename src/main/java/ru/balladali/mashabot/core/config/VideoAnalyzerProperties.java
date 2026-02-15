package ru.balladali.mashabot.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clients.video-analyzer")
public record VideoAnalyzerProperties(
        String baseUrl,
        Integer timeoutSec
) {}
