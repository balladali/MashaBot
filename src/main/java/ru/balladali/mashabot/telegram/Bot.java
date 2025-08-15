package ru.balladali.mashabot.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.balladali.mashabot.core.handlers.message.MessageHandler;

import java.util.List;

@Component
public class Bot implements LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;

    @Autowired
    private List<MessageHandler> messageHandlers;

    public Bot(TelegramClient telegramClient, List<MessageHandler> messageHandlers) {
        this.telegramClient = telegramClient;
        this.messageHandlers = messageHandlers;
    }

    private void handleMessage(Message message) {
        TelegramMessage messageEntity = new TelegramMessage(message, this.telegramClient);
        for (MessageHandler messageHandler : messageHandlers) {
            if (messageHandler.needHandle(messageEntity)) {
                messageHandler.handle(messageEntity);
                return;
            }
        }
    }

    @Override
    public void consume(Update update) {
        Message message = update.getMessage();
        if (message != null) {
            handleMessage(message);
        }
    }
}
