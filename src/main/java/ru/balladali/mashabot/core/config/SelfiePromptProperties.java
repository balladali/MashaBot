package ru.balladali.mashabot.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "selfie.prompt")
public record SelfiePromptProperties(
        String base,
        String withUserScene,
        String withRandomScene
) {}
