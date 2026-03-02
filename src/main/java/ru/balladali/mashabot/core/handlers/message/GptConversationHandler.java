package ru.balladali.mashabot.core.handlers.message;

import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessageDraft;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;
import ru.balladali.mashabot.telegram.TelegramMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class GptConversationHandler implements MessageHandler {
    private final ChatGptClient chat;
    private final String personaSystemPrompt;
    private final boolean streamEnabled;
    private static final int TG_LIMIT = 4096;
    private static final Pattern TRIGGER = Pattern.compile("^(?:маша[\\s,:-]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public GptConversationHandler(ChatGptClient chat, String personaSystemPrompt, boolean streamEnabled) {
        this.chat = chat;
        this.personaSystemPrompt = personaSystemPrompt;
        this.streamEnabled = streamEnabled;
    }

    @Override
    public boolean needHandle(TelegramMessage message) {
        if (message == null || message.getText() == null) return false;
        return TRIGGER.matcher(message.getText()).find();
    }

    @Override
    public void handle(TelegramMessage entity) {
        String text = Optional.ofNullable(entity.getText()).orElse("");

        String userQuery = TRIGGER.matcher(text).replaceFirst("").trim();

        String reply = "";
        String caption = "";
        if (entity.getMessage().getReplyToMessage() != null) {
            reply = entity.getMessage().getReplyToMessage().getText();
            caption = entity.getMessage().getReplyToMessage().getCaption();
        }
        if (userQuery.isEmpty() && (reply == null || reply.isBlank()) && (caption == null || caption.isBlank())) {
            sendAnswer(entity, "Привет! Я Маша 🦄 Чем могу помочь?");
            return;
        }

        List<ChatGptClient.ChatMessage> messages = getChatMessages(reply, caption, userQuery);
        try {
            sendTyping(entity);

            Integer draftId = resolveDraftId(entity.getMessage());
            if (streamEnabled && draftId != null) {
                StringBuilder acc = new StringBuilder();
                final long[] lastPushMs = {0L};

                chat.chatStream(messages, 0.8, 600, delta -> {
                    acc.append(delta);
                    long now = System.currentTimeMillis();
                    if (now - lastPushMs[0] >= 350 && !acc.isEmpty()) {
                        sendDraft(entity, draftId, acc.toString());
                        lastPushMs[0] = now;
                    }
                });

                if (!acc.isEmpty()) {
                    sendDraft(entity, draftId, acc.toString());
                    return;
                }
            }

            String answer = chat.chat(messages, 0.8, 600);
            sendAnswer(entity, answer);
        } catch (Exception e) {
            e.printStackTrace();
            sendAnswer(entity, "Ой, тут что-то пошло не так… Давай попробуем ещё раз чуток позже?");
        }
    }

    private Integer resolveDraftId(Message message) {
        if (message == null) return null;
        if (message.getDirectMessagesTopic() != null && message.getDirectMessagesTopic().getTopicId() != null) {
            long topicId = message.getDirectMessagesTopic().getTopicId();
            if (topicId > 0 && topicId <= Integer.MAX_VALUE) {
                return (int) topicId;
            }
        }
        return null;
    }

    private void sendDraft(TelegramMessage messageEntity, Integer draftId, String text) {
        if (text == null || text.isBlank()) return;
        try {
            Long chatId = Long.parseLong(messageEntity.getChatId());
            Integer threadId = messageEntity.getMessage().getMessageThreadId();
            SendMessageDraft draft = new SendMessageDraft(chatId, threadId, draftId, text, null, null);
            messageEntity.getClient().execute(draft);
        } catch (Exception ignored) {
        }
    }

    @NotNull
    private List<ChatGptClient.ChatMessage> getChatMessages(String reply, String caption, String userQuery) {
        List<ChatGptClient.ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatGptClient.ChatMessage("system", personaSystemPrompt));
        String context = "";
        if (reply != null && !reply.isBlank()) {
            context += reply;
        }
        if (caption != null && !caption.isBlank()) {
            context += "\n" + caption;
        }
        if (!context.isBlank()) {
            messages.add(new ChatGptClient.ChatMessage("system", "Контекст предыдущего сообщения пользователя: «" + context + "»."));
        }
        messages.add(new ChatGptClient.ChatMessage("user", userQuery));
        return messages;
    }

    @Override
    public void sendAnswer(TelegramMessage messageEntity, String answer) {
        String t = (answer == null) ? "" : answer.strip();
        if (t.isEmpty()) {
            return;
        }

        for (String part : splitForTelegram(t, TG_LIMIT)) {
            SendMessage msg = new SendMessage(messageEntity.getChatId(), part);
            try {
                messageEntity.getClient().execute(msg);
            } catch (TelegramApiException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private static List<String> splitForTelegram(String s, int limit) {
        List<String> parts = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            if (buf.length() + line.length() + 1 > limit) {
                if (!buf.isEmpty()) {
                    parts.add(buf.toString());
                    buf.setLength(0);
                }
                while (line.length() > limit) {
                    parts.add(line.substring(0, limit));
                    line = line.substring(limit);
                }
            }
            if (!buf.isEmpty()) buf.append('\n');
            buf.append(line);
        }
        if (!buf.isEmpty()) parts.add(buf.toString());
        return parts;
    }

    private void sendTyping(TelegramMessage messageEntity) {
        SendChatAction action = new SendChatAction(messageEntity.getChatId(), ActionType.TYPING.toString());
        try {
            messageEntity.getClient().execute(action);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
