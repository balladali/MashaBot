package ru.balladali.balladalibot.balladalibot.core.handlers.message;

import co.aurasphere.jyandex.Jyandex;
import ru.balladali.balladalibot.balladalibot.core.entity.Language;
import ru.balladali.balladalibot.balladalibot.core.entity.MessageEntity;

import java.util.Arrays;

public class YandexTranslateHandler implements MessageHandler {

    private Jyandex jyandex;

    public YandexTranslateHandler(Jyandex jyandex) {
        this.jyandex = jyandex;
    }

    @Override

    public String answer(MessageEntity entity) {
        String languageFromMessage = entity.getText().toLowerCase();
        String toTranslate = entity.getReply();
        if (needAnswer(languageFromMessage) && toTranslate != null) {
            String[] translatedText = jyandex.translateText(toTranslate, languageFromMessage).getTranslatedText();
            if (translatedText == null) {
                return "Извини, не могу :(";
            }
            return String.join(" ", translatedText);
        }
        return null;
    }

    @Override
    public boolean needAnswer(String message) {
        return Arrays.asList(Language.values()).contains(Language.fromString(message));
    }
}