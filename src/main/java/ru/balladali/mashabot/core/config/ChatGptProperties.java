package ru.balladali.mashabot.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "clients.chatgpt")
public record ChatGptProperties(
        String baseUrl,
        String apiKey,
        String model,
        boolean reasoningEnabled,
        @DefaultValue("1500") int maxTokens,
        int timeoutSec,
        int memoryMessages,
        int memoryTtlMinutes,
        int summaryEveryUserMessages,
        String profileDir
) {
    public ChatGptProperties {
        if (maxTokens < 1) throw new IllegalArgumentException("clients.chatgpt.max-tokens must be positive");
    }
}
