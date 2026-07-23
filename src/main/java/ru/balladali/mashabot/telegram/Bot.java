package ru.balladali.mashabot.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.balladali.mashabot.core.handlers.message.MessageHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

@Component
public class Bot implements LongPollingSingleThreadUpdateConsumer {

    private static final int TELEGRAM_MESSAGE_LIMIT = 4096;

    private final TelegramClient telegramClient;
    private final String errorChatId;

    @Autowired
    private List<MessageHandler> messageHandlers;

    public Bot(TelegramClient telegramClient, List<MessageHandler> messageHandlers,
               @Value("${bot.error-chat-id:}") String errorChatId) {
        this.telegramClient = telegramClient;
        this.messageHandlers = messageHandlers;
        this.errorChatId = errorChatId;
    }

    private void handleMessage(Message message) {
        TelegramMessage messageEntity = new TelegramMessage(message, this.telegramClient);
        for (MessageHandler messageHandler : messageHandlers) {
            if (messageHandler.needHandle(messageEntity)) {
                try {
                    messageHandler.handle(messageEntity);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendErrorNotification(e, messageEntity.getChatId());
                }
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

    private void sendErrorNotification(Exception error, String sourceChatId) {
        if (errorChatId == null || errorChatId.isBlank()) return;

        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        String text = maskSecrets("MashaBot error (source chat: " + sourceChatId + ")\n\n" + trace);

        for (int start = 0; start < text.length(); start += TELEGRAM_MESSAGE_LIMIT) {
            String part = text.substring(start, Math.min(start + TELEGRAM_MESSAGE_LIMIT, text.length()));
            try {
                telegramClient.execute(new SendMessage(errorChatId, part));
            } catch (TelegramApiException notificationError) {
                notificationError.printStackTrace();
                return;
            }
        }
    }

    private String maskSecrets(String text) {
        return text
                .replaceAll("(?i)(Bearer\\s+)[^\\s]+", "$1***")
                .replaceAll("(?i)((?:api[_-]?key|token|password|authorization)\\s*[=:]\\s*)[^\\s]+", "$1***")
                .replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
    }
}