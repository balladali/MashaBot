package ru.balladali.balladalibot.balladalibot.core.handlers.message;

import ru.balladali.balladalibot.balladalibot.telegram.TelegramMessage;

public interface MessageHandler {

    void handle(TelegramMessage entity);

    boolean needHandle(String message);

    void sendAnswer(TelegramMessage messageEntity, String answer);
}
