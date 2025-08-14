package ru.balladali.mashabot.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clients.chatgpt")
public record ChatGptProperties(
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSec
) {}
