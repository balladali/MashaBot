package ru.balladali.mashabot.core.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;
import ru.balladali.mashabot.core.clients.selfie.FalSelfieClient;
import ru.balladali.mashabot.core.clients.video.VideoAnalyzerClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({ChatGptProperties.class, VideoAnalyzerProperties.class, FalProperties.class})
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

    @Bean
    public VideoAnalyzerClient videoAnalyzerClient(OkHttpClient client, VideoAnalyzerProperties props) {
        return new VideoAnalyzerClient(
                client,
                props.baseUrl(),
                Duration.ofSeconds(props.timeoutSec() == null ? 60 : props.timeoutSec())
        );
    }

    @Bean
    public FalSelfieClient falSelfieClient(OkHttpClient client, FalProperties props,
                                           @org.springframework.beans.factory.annotation.Value("${credential.fal.api-key:}") String apiKey) {
        String base = (props.baseUrl() == null || props.baseUrl().isBlank()) ? "https://fal.run" : props.baseUrl();
        String modelPath = (props.modelPath() == null || props.modelPath().isBlank())
                ? "fal-ai/flux/dev/image-to-image"
                : props.modelPath();
        String endpoint = base.endsWith("/") ? base + modelPath : base + "/" + modelPath;
        return new FalSelfieClient(client, endpoint, apiKey,
                Duration.ofSeconds(props.timeoutSec() == null ? 90 : props.timeoutSec()));
    }

}
