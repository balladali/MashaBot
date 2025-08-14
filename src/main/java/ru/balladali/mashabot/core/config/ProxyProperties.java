package ru.balladali.mashabot.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "telegram.proxy")
@ConstructorBinding
public record ProxyProperties(
        Type type,
        String host,
        int port,
        String user,
        String pass,
        int connectTimeoutMs,
        int readTimeoutMs,
        int writeTimeoutMs
) {
    public enum Type { NONE, HTTP, SOCKS }

    // значения по умолчанию
    public ProxyProperties {
        if (type == null) type = Type.NONE;
        if (connectTimeoutMs <= 0) connectTimeoutMs = 15000;
        if (readTimeoutMs    <= 0) readTimeoutMs    = 30000;
        if (writeTimeoutMs   <= 0) writeTimeoutMs   = 30000;
    }
}