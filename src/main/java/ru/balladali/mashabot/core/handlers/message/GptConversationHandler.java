package ru.balladali.mashabot.core.handlers.message;

import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;
import ru.balladali.mashabot.core.services.DialogMemoryStore;
import ru.balladali.mashabot.telegram.TelegramMessage;

import java.util.*;
import java.util.regex.Pattern;

public class GptConversationHandler implements MessageHandler {
    private final ChatGptClient chat;
    private final String personaSystemPrompt;
    private final int memoryMessages;
    private final long memoryTtlMs;
    private static final int TG_LIMIT = 4096;
    private static final Pattern TRIGGER = Pattern.compile("^(?:маша[\\s,:-]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static volatile Long BOT_ID = null;

    public GptConversationHandler(ChatGptClient chat, String personaSystemPrompt, int memoryMessages, int memoryTtlMinutes) {
        this.chat = chat;
        this.personaSystemPrompt = personaSystemPrompt;
        this.memoryMessages = Math.max(1, memoryMessages);
        this.memoryTtlMs = Math.max(1, memoryTtlMinutes) * 60_000L;
    }

    @Override
    public boolean needHandle(TelegramMessage message) {
        if (message == null) return false;

        String text = message.getText();
        boolean hasTrigger = text != null && TRIGGER.matcher(text).find();
        return hasTrigger || isReplyToMashaMessage(message);
    }

    private boolean isReplyToMashaMessage(TelegramMessage telegramMessage) {
        Message message = telegramMessage.getMessage();
        if (message == null || message.getReplyToMessage() == null || message.getReplyToMessage().getFrom() == null) {
            return false;
        }

        Long mashaId = resolveBotId(telegramMessage);
        if (mashaId == null) return false;

        return Objects.equals(message.getReplyToMessage().getFrom().getId(), mashaId);
    }

    private Long resolveBotId(TelegramMessage telegramMessage) {
        if (BOT_ID != null) return BOT_ID;
        try {
            User me = telegramMessage.getClient().execute(new GetMe());
            if (me != null && me.getId() != null) {
                BOT_ID = me.getId();
                return BOT_ID;
            }
        } catch (Exception ignored) {
        }
        return null;
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

        String memoryKey = DialogMemoryStore.keyFromMessage(entity.getMessage());
        List<ChatGptClient.ChatMessage> messages = getChatMessages(memoryKey, reply, caption, userQuery);
        try {
            sendTyping(entity);

            String answer = chat.chat(messages, 0.8, 600);
            sendAnswer(entity, answer);
            DialogMemoryStore.append(memoryKey, "user", userQuery, memoryMessages, memoryTtlMs);
            DialogMemoryStore.append(memoryKey, "assistant", answer, memoryMessages, memoryTtlMs);
        } catch (Exception e) {
            e.printStackTrace();
            sendAnswer(entity, "Ой, тут что-то пошло не так… Давай попробуем ещё раз чуток позже?");
        }
    }

    @NotNull
    private List<ChatGptClient.ChatMessage> getChatMessages(String memoryKey, String reply, String caption, String userQuery) {
        List<ChatGptClient.ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatGptClient.ChatMessage("system", personaSystemPrompt));

        List<DialogMemoryStore.Turn> history = DialogMemoryStore.getHistory(memoryKey, memoryTtlMs);
        for (DialogMemoryStore.Turn turn : history) {
            messages.add(new ChatGptClient.ChatMessage(turn.role(), turn.content()));
        }

        String context = "";
        if (reply != null && !reply.isBlank()) {
            context += reply;
        }
        if (caption != null && !caption.isBlank()) {
            context += "\n" + caption;
        }
        if (!context.isBlank()) {
            messages.add(new ChatGptClient.ChatMessage("system", "Контекст сообщения, на которое пользователь ответил: «" + context + "»."));
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

        boolean firstPart = true;
        Integer replyToMessageId = messageEntity.getMessage() != null ? messageEntity.getMessage().getMessageId() : null;

        for (String part : splitForTelegram(t, TG_LIMIT)) {
            SendMessage msg = new SendMessage(messageEntity.getChatId(), part);
            if (firstPart && replyToMessageId != null) {
                msg.setReplyToMessageId(replyToMessageId);
            }
            try {
                messageEntity.getClient().execute(msg);
                firstPart = false;
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
