package ru.balladali.balladalibot.balladalibot.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import ru.balladali.balladalibot.balladalibot.core.handlers.ConversationHandler;
import ru.balladali.balladalibot.balladalibot.core.handlers.YouTubeHandler;
import ru.balladali.balladalibot.balladalibot.core.services.YouTubeService;

import javax.annotation.Nonnull;

@Configuration
public class HandlerConfig {
    @Bean(name = "conversationHandler")
    @Order(Integer.MAX_VALUE)
    public ConversationHandler getConversationHandler() {
        return new ConversationHandler();
    }

    @Autowired
    @Order(1)
    @Bean(name = "youtubeHandler")
    public YouTubeHandler getYouTubeHandler(@Nonnull YouTubeService youTubeService) {
        return new YouTubeHandler(youTubeService);
    }
}
