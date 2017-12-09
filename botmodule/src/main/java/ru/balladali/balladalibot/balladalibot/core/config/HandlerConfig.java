package ru.balladali.balladalibot.balladalibot.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.balladali.balladalibot.balladalibot.core.handlers.ConversationHandler;

@Configuration
public class HandlerConfig {
    @Bean(name = "conversationHandler")
    public ConversationHandler getConversationHandler() {
        return new ConversationHandler();
    }
}
