package ru.balladali.mashabot.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "masha")
public record MashaProperties(
        String persona
) {}
