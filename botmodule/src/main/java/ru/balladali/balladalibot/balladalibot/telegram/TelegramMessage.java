package ru.balladali.balladalibot.balladalibot.telegram;

import org.telegram.telegrambots.api.objects.Message;
import ru.balladali.balladalibot.balladalibot.core.MessageEntity;

public class TelegramMessage implements MessageEntity {

    private Message message;

    public TelegramMessage(Message message) {
        this.message = message;
    }

    @Override
    public String getText() {
        return message.getText();
    }

    @Override
    public String getChatId() {
        return String.valueOf(message.getChatId());
    }

    @Override
    public String getSender() {
        return message.getFrom().getFirstName() + " " + message.getFrom().getLastName();
    }
}
