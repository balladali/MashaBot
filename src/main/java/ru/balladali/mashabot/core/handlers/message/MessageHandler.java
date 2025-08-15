package ru.balladali.mashabot.core.handlers.message;

import ru.balladali.mashabot.telegram.TelegramMessage;

public interface MessageHandler {

    void handle(TelegramMessage entity);

    boolean needHandle(TelegramMessage message);

    void sendAnswer(TelegramMessage messageEntity, String answer);
}
