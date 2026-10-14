package ru.balladali.mashabot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@SpringBootTest(properties = {
        "credential.telegram.token=test-only", "clients.chatgpt.base-url=http://127.0.0.1:1",
        "clients.chatgpt.api-key=test-only", "clients.chatgpt.model=test-model", "ping-job.enabled=false"
})
class BotApplicationTests {
    @MockitoBean TelegramBotsLongPollingApplication telegramApp;
    @MockitoBean TelegramClient telegramClient;
    @Test void contextLoadsWithoutExternalServices() {}
}
