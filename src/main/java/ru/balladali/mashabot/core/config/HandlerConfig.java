package ru.balladali.mashabot.core.config;

import co.aurasphere.jyandex.Jyandex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import ru.balladali.mashabot.core.clients.exchange.ExchangeRateClient;
import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;
import ru.balladali.mashabot.core.clients.video.VideoAnalyzerClient;
import ru.balladali.mashabot.core.handlers.message.*;
import ru.balladali.mashabot.core.services.YandexSpeechService;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(MashaProperties.class)
public class HandlerConfig {

    @Bean(name = "conversationHandler")
    @Order(Integer.MAX_VALUE)
    public ConversationHandler getConversationHandler(YandexSpeechService yandexSpeechService) {
        return new ConversationHandler(yandexSpeechService);
    }

    @Autowired
    @Order(1)
    @Bean("yandexTranslateHandler")
    public YandexTranslateHandler getYandexTranslateHandler(@Nonnull Jyandex jyandex) {
        return new YandexTranslateHandler(jyandex);
    }

    @Autowired
    @Order(4)
    @Bean("currencyConvertHandler")
    public CurrencyConvertHandler currencyConvertHandler(ExchangeRateClient exchangeRateClient) {
        return new CurrencyConvertHandler(exchangeRateClient);
    }

    @Autowired
    @Order(3)
    @Bean("gptConversationHandler")
    public GptConversationHandler gptConversationHandler(ChatGptClient client, MashaProperties mashaProperties) {
        return new GptConversationHandler(client, mashaProperties.persona());
    }

    @Autowired
    @Order(2)
    @Bean("videoAnalyzeHandler")
    public VideoAnalyzeHandler videoAnalyzeHandler(VideoAnalyzerClient client) {
        return new VideoAnalyzeHandler(client);
    }

    @Bean
    public List<MessageHandler> messageHandlers(Map<String, MessageHandler> messageHandlers) {
        List<MessageHandler> messageHandlersList = new ArrayList<>();
        messageHandlersList.add(messageHandlers.get("yandexTranslateHandler"));
        messageHandlersList.add(messageHandlers.get("videoAnalyzeHandler"));
        messageHandlersList.add(messageHandlers.get("gptConversationHandler"));
        messageHandlersList.add(messageHandlers.get("conversationHandler"));
        messageHandlersList.add(messageHandlers.get("currencyConvertHandler"));
        return messageHandlersList;
    }
}
