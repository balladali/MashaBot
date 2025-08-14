package ru.balladali.mashabot.core.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ChatGptProperties.class)
public class ClientsConfiguration {
    @Bean
    public ChatGptClient chatGptClient(OkHttpClient client, ChatGptProperties props) {
        return new ChatGptClient(
                client,
                props.baseUrl(),
                props.apiKey(),
                props.model(),
                Duration.ofSeconds(props.timeoutSec())
        );
    }

}
