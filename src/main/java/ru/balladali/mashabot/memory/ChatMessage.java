package ru.balladali.mashabot.memory;

import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;

public record ChatMessage(
        long chatId,
        long messageId,
        Long threadId,
        Long userId,
        String username,
        String displayName,
        boolean bot,
        String direction,
        Long replyId,
        String type,
        String text,
        Instant date,
        Instant edited) {
    public static ChatMessage from(Message m, boolean outgoing) {
        var u = m.getFrom();
        String type =
                m.hasText()
                        ? "TEXT"
                        : m.hasPhoto()
                                ? "PHOTO"
                                : m.hasVideo()
                                        ? "VIDEO"
                                        : m.hasVoice()
                                                ? "VOICE"
                                                : m.hasAudio()
                                                        ? "AUDIO"
                                                        : m.hasDocument()
                                                                ? "DOCUMENT"
                                                                : m.hasSticker()
                                                                        ? "STICKER"
                                                                        : "OTHER";
        return new ChatMessage(
                m.getChatId(),
                m.getMessageId().longValue(),
                m.getMessageThreadId() == null ? null : m.getMessageThreadId().longValue(),
                u == null ? null : u.getId(),
                u == null ? null : u.getUserName(),
                u == null
                        ? "Unknown"
                        : (u.getFirstName()
                                        + " "
                                        + (u.getLastName() == null ? "" : u.getLastName()))
                                .strip(),
                outgoing || u != null && Boolean.TRUE.equals(u.getIsBot()),
                outgoing ? "OUTGOING" : "INCOMING",
                m.getReplyToMessage() == null
                        ? null
                        : m.getReplyToMessage().getMessageId().longValue(),
                type,
                m.hasText() ? m.getText() : m.getCaption(),
                Instant.ofEpochSecond(m.getDate()),
                m.getEditDate() == null ? null : Instant.ofEpochSecond(m.getEditDate()));
    }

    public String formatted() {
        return "["
                + date
                + "] "
                + displayName
                + " (message="
                + messageId
                + ", reply="
                + replyId
                + ", bot="
                + bot
                + "):\n"
                + (text == null ? "[" + type + "]" : text);
    }
}
