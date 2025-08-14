package ru.balladali.mashabot.core.handlers.message;

import co.aurasphere.jyandex.Jyandex;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.balladali.mashabot.core.entity.Language;
import ru.balladali.mashabot.telegram.TelegramMessage;

import java.util.Arrays;

public class YandexTranslateHandler implements MessageHandler {

    private Jyandex jyandex;

    public YandexTranslateHandler(Jyandex jyandex) {
        this.jyandex = jyandex;
    }

    @Override
    public void handle(TelegramMessage entity) {
        String languageFromMessage = entity.getText().toLowerCase();
        String toTranslate = entity.getReply();
        if (needHandle(languageFromMessage) && toTranslate != null) {
            String[] translatedText = jyandex.translateText(toTranslate, languageFromMessage).getTranslatedText();
            if (translatedText == null) {
                sendAnswer(entity,"Извини, не могу :(");
            }
            sendAnswer(entity, String.join(" ", translatedText));
        }
    }

    @Override
    public boolean needHandle(String message) {
        return Arrays.asList(Language.values()).contains(Language.fromString(message));
    }

    @Override
    public void sendAnswer(TelegramMessage messageEntity, String answer) {
        SendMessage sendMessage = new SendMessage(messageEntity.getChatId(), answer);
        try {
            messageEntity.getClient().execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}