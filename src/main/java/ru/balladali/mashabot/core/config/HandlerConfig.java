package ru.balladali.mashabot.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import ru.balladali.mashabot.core.clients.exchange.ExchangeRateClient;
import org.springframework.ai.chat.client.ChatClient;
import ru.balladali.mashabot.core.clients.video.VideoAnalyzerClient;
import ru.balladali.mashabot.core.handlers.message.*;
import ru.balladali.mashabot.core.services.ProfileSummaryService;
import ru.balladali.mashabot.core.services.YandexSpeechService;

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

    @Order(4)
    @Bean("currencyConvertHandler")
    public CurrencyConvertHandler currencyConvertHandler(ExchangeRateClient exchangeRateClient) {
        return new CurrencyConvertHandler(exchangeRateClient);
    }

    @Bean
    public ProfileSummaryService profileSummaryService(ChatClient chatClient, ChatGptProperties chatGptProperties) {
        return new ProfileSummaryService(chatClient, chatGptProperties.model(), chatGptProperties.profileDir());
    }

    @Order(3)
    @Bean("gptConversationHandler")
    public GptConversationHandler gptConversationHandler(ChatClient chatClient,
                                                         MashaProperties mashaProperties,
                                                         ChatGptProperties chatGptProperties,
                                                         ProfileSummaryService profileSummaryService) {
        return new GptConversationHandler(
                chatClient,
                chatGptProperties.model(),
                mashaProperties.persona(),
                chatGptProperties.memoryMessages(),
                chatGptProperties.memoryTtlMinutes(),
                chatGptProperties.summaryEveryUserMessages(),
                profileSummaryService
        );
    }

    @Order(2)
    @Bean("videoAnalyzeHandler")
    public VideoAnalyzeHandler videoAnalyzeHandler(VideoAnalyzerClient client) {
        return new VideoAnalyzeHandler(client);
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
