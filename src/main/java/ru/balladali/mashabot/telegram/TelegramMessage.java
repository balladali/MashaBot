package ru.balladali.mashabot.telegram;

import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.balladali.mashabot.core.entity.MessageEntity;

public class TelegramMessage implements MessageEntity {

    private Message message;

    private TelegramClient client;

    public TelegramMessage(Message message, TelegramClient client) {
        this.message = message;
        this.client = client;
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
    public String getSenderName() {
        return message.getFrom().getFirstName() + " " + message.getFrom().getLastName();
    }

    @Override
    public String getReply() {
        Message replyMessage = message.getReplyToMessage();
        if (replyMessage != null) {
            return replyMessage.getText();
        }
        return null;
    }

    public Message getMessage() {
        return message;
    }

    public TelegramClient getClient() {
        return client;
    }
}
