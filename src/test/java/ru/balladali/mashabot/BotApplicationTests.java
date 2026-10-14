package ru.balladali.mashabot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.springframework.beans.factory.annotation.Autowired;
import ru.balladali.mashabot.core.config.ChatGptProperties;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "credential.telegram.token=test-only", "clients.chatgpt.base-url=http://127.0.0.1:1",
        "clients.chatgpt.api-key=test-only", "clients.chatgpt.model=test-model", "ping-job.enabled=false"
})
class BotApplicationTests {
    @MockitoBean TelegramBotsLongPollingApplication telegramApp;
    @MockitoBean TelegramClient telegramClient;
    @Autowired ChatGptProperties chatGptProperties;
    @Test void contextLoadsWithoutExternalServices() {
        assertFalse(chatGptProperties.reasoningEnabled());
        assertEquals(1500, chatGptProperties.maxTokens());
    }
}
