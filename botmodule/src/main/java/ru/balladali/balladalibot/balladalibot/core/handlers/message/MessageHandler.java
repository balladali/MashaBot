package ru.balladali.balladalibot.balladalibot.core.handlers.message;

import ru.balladali.balladalibot.balladalibot.core.entity.MessageEntity;

public interface MessageHandler {
    String answer(MessageEntity entity);

    boolean needAnswer(String message);
}
