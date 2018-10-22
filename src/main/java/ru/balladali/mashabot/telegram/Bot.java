package ru.balladali.mashabot.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import ru.balladali.mashabot.core.handlers.inline.InlineHandler;
import ru.balladali.mashabot.core.handlers.message.MessageHandler;
import ru.balladali.mashabot.core.services.YandexSpeechService;

import javax.annotation.PreDestroy;
import java.util.List;

public class Bot extends TelegramLongPollingBot {

    @Value("${credential.telegram.login}")
    private String botName;

    @Value("${credential.telegram.token}")
    private String botToken;

    private DefaultBotOptions botOptions;

    @Autowired
    private List<MessageHandler> messageHandlers;

    @Autowired
    private InlineHandler inlineHandler;

    @Autowired
    private YandexSpeechService yandexSpeechService;

    public Bot(DefaultBotOptions botOptions) {
        super(botOptions);
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        InlineQuery inlineQuery = update.getInlineQuery();
        if (message != null) {
            handleMessage(message);
        }

        if (inlineQuery != null) {
            handleInline(inlineQuery);
        }

    }

    private void handleMessage(Message message) {
        TelegramMessage messageEntity = new TelegramMessage(message, this);
        for (MessageHandler messageHandler : messageHandlers) {
            if (messageHandler.needHandle(message.getText())) {
                messageHandler.handle(messageEntity);
                return;
            }
        }
    }

    private void handleInline(InlineQuery inlineQuery) {
        if (inlineQuery.hasQuery()) {
            List<InlineQueryResult> answer = (List<InlineQueryResult>) inlineHandler.answerInline(inlineQuery);
            AnswerInlineQuery answerInlineQuery = new AnswerInlineQuery();
            answerInlineQuery.setInlineQueryId(inlineQuery.getId());
            answerInlineQuery.setResults(answer);
            try {
                execute(answerInlineQuery);
            } catch (TelegramApiException e) {
                e.printStackTrace();
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
