package ru.balladali.mashabot.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import ru.balladali.mashabot.core.clients.exchange.ExchangeRateClient;
import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;
import ru.balladali.mashabot.core.clients.video.VideoAnalyzerClient;
import ru.balladali.mashabot.core.handlers.message.*;
import ru.balladali.mashabot.core.services.YandexSpeechService;
import ru.balladali.mashabot.core.services.KeyedTaskExecutor;
import ru.balladali.mashabot.telegram.TelegramDelivery;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(MashaProperties.class)
public class HandlerConfig {

    @Bean(name = "conversationHandler")
    @Order(Integer.MAX_VALUE)
    public ConversationHandler getConversationHandler(YandexSpeechService yandexSpeechService, TelegramDelivery delivery) {
        return new ConversationHandler(yandexSpeechService, delivery);
    }

    @Order(4)
    @Bean("currencyConvertHandler")
    public CurrencyConvertHandler currencyConvertHandler(ExchangeRateClient exchangeRateClient, TelegramDelivery delivery) {
        return new CurrencyConvertHandler(exchangeRateClient, delivery);
    }

    @Order(3)
    @Bean("gptConversationHandler")
    public GptConversationHandler gptConversationHandler(ChatGptClient client, MashaProperties mashaProperties, ChatGptProperties chatGptProperties,
                                                       @Qualifier("summaryExecutor") KeyedTaskExecutor summaries, TelegramDelivery delivery) {
        return new GptConversationHandler(
                client,
                mashaProperties.persona(),
                chatGptProperties.maxTokens(),
                chatGptProperties.memoryMessages(),
                chatGptProperties.memoryTtlMinutes(),
                chatGptProperties.summaryEveryUserMessages(),
                chatGptProperties.profileDir(), summaries, delivery
        );
    }

    @Order(2)
    @Bean("videoAnalyzeHandler")
    public VideoAnalyzeHandler videoAnalyzeHandler(VideoAnalyzerClient client, TelegramDelivery delivery) {
        return new VideoAnalyzeHandler(client, delivery);
    }

    @Bean
    public List<MessageHandler> messageHandlers(Map<String, MessageHandler> messageHandlers) {
        List<MessageHandler> messageHandlersList = new ArrayList<>();
        messageHandlersList.add(messageHandlers.get("videoAnalyzeHandler"));
        messageHandlersList.add(messageHandlers.get("gptConversationHandler"));
        messageHandlersList.add(messageHandlers.get("conversationHandler"));
        messageHandlersList.add(messageHandlers.get("currencyConvertHandler"));
        return messageHandlersList;
    }
}
