package ru.balladali.mashabot.core.config;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.balladali.mashabot.core.services.YouTubeService;

import javax.annotation.Nonnull;

@Configuration
public class YouTubeConfig {
    @Bean("youtube")
    public YouTube getYouTube() {
        return new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), request -> {})
                .setApplicationName("Masha-bot")
                .build();
    }

    @Bean("youTubeService")
    @Autowired
    public YouTubeService getYouTubeService(@Nonnull YouTube youTube) {
        return new YouTubeService(youTube);
    }
}
