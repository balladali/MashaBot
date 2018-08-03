package ru.balladali.balladalibot.balladalibot.core.config;

import co.aurasphere.jyandex.Jyandex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import ru.balladali.balladalibot.balladalibot.core.handlers.inline.YouTubeInlineHandler;
import ru.balladali.balladalibot.balladalibot.core.handlers.message.ConversationHandler;
import ru.balladali.balladalibot.balladalibot.core.handlers.message.MessageHandler;
import ru.balladali.balladalibot.balladalibot.core.handlers.message.YandexTranslateHandler;
import ru.balladali.balladalibot.balladalibot.core.handlers.message.YouTubeHandler;
import ru.balladali.balladalibot.balladalibot.core.services.YandexSpeechService;
import ru.balladali.balladalibot.balladalibot.core.services.YouTubeService;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class HandlerConfig {

    @Bean(name = "conversationHandler")
    @Order(Integer.MAX_VALUE)
    public ConversationHandler getConversationHandler(YandexSpeechService yandexSpeechService) {
        return new ConversationHandler(yandexSpeechService);
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

    @Bean
    public List<MessageHandler> messageHandlers(Map<String, MessageHandler> messageHandlers) {
        List<MessageHandler> messageHandlersList = new ArrayList<>();
        messageHandlersList.add(messageHandlers.get("youtubeHandler"));
        messageHandlersList.add(messageHandlers.get("yandexTranslateHandler"));
        messageHandlersList.add(messageHandlers.get("conversationHandler"));
        return messageHandlersList;
    }
}
