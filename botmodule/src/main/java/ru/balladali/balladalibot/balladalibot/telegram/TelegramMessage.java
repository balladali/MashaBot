package ru.balladali.balladalibot.balladalibot.telegram;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.balladali.balladalibot.balladalibot.core.entity.MessageEntity;

public class TelegramMessage implements MessageEntity {

    private Message message;

    private AbsSender sender;

    public TelegramMessage(Message message, AbsSender sender) {
        this.message = message;
        this.sender = sender;
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

    public AbsSender getSender() {
        return sender;
    }
}
