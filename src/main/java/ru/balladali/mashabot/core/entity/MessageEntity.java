package ru.balladali.mashabot.core.entity;

public interface MessageEntity {
    String getText();

    String getChatId();

    String getSenderName();

    String getReply();

    String getCaption();
}
