package ru.balladali.balladalibot.balladalibot.core;

import ru.balladali.balladalibot.balladalibot.core.entity.MessageEntity;

public interface MessageHandler {
    String answer(MessageEntity entity);
}
