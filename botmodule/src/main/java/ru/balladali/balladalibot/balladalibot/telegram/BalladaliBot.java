package ru.balladali.balladalibot.balladalibot.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import ru.balladali.balladalibot.balladalibot.core.entity.MessageEntity;
import ru.balladali.balladalibot.balladalibot.core.MessageHandler;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class BalladaliBot extends TelegramLongPollingBot {

    @Value("${credential.telegram.login}")
    private String botName;

    @Value("${credential.telegram.token}")
    private String botToken;

    @Autowired
    MessageHandler messageHandler;

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        if (message != null) {
            MessageEntity messageEntity = new TelegramMessage(message);
            String answer = messageHandler.answer(messageEntity);
            if (answer != null) {
                SendMessage sendMessage = new SendMessage(messageEntity.getChatId(), answer);
                try {
                    execute(sendMessage);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public String getBotUsername() {
        return botName;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @PostConstruct
    public void init() {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            telegramBotsApi.registerBot(this);
        } catch (TelegramApiRequestException e) {
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void destroy() {

    }
}
