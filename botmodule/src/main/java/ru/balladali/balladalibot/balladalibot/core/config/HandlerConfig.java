package ru.balladali.balladalibot.balladalibot.core.config;

import co.aurasphere.jyandex.Jyandex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import ru.balladali.balladalibot.balladalibot.core.handlers.inline.YouTubeInlineHandler;
import ru.balladali.balladalibot.balladalibot.core.handlers.message.ConversationHandler;
import ru.balladali.balladalibot.balladalibot.core.handlers.message.YandexTranslateHandler;
import ru.balladali.balladalibot.balladalibot.core.handlers.message.YouTubeHandler;
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

    @Autowired
    @Bean("youtubeInlineHandler")
    public YouTubeInlineHandler getYouTubeInlineHandler(@Nonnull YouTubeService youTubeService) {
        return new YouTubeInlineHandler(youTubeService);
    }

    @Autowired
    @Order(2)
    @Bean("yandexTranslateHandler")
    public YandexTranslateHandler getYandexTranslateHandler(@Nonnull Jyandex jyandex) {
        return new YandexTranslateHandler(jyandex);
    }
}
