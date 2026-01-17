package ru.balladali.mashabot.core.handlers.message;

import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
    private static final int TG_LIMIT = 4096;
    private static final Pattern TRIGGER = Pattern.compile("^(?:маша[\\s,:-]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public GptConversationHandler(ChatGptClient chat, String personaSystemPrompt) {
        this.chat = chat;
        this.personaSystemPrompt = personaSystemPrompt;
    }

    @Override
    public boolean needHandle(TelegramMessage message) {
        if (message == null || message.getText() == null) return false;
        return TRIGGER.matcher(message.getText()).find();
    }

    @Override
    public void handle(TelegramMessage entity) {
        String text = Optional.ofNullable(entity.getText()).orElse("");

        // 1) срезаем триггер "Маша" в начале
        String userQuery = TRIGGER.matcher(text).replaceFirst("").trim();

        // 2) собираем контекст: если это reply или caption — добавим как system-hint
        String reply = "";
        String caption = "";
        if (entity.getMessage().getReplyToMessage() != null) {
            reply = entity.getMessage().getReplyToMessage().getText();
            caption = entity.getMessage().getReplyToMessage().getCaption();
        }
        if (userQuery.isEmpty() && (reply == null || reply.isBlank()) && (caption == null || caption.isBlank())) {
            sendAnswer(entity, "Привет! Я Маша \uD83E\uDD84 Чем могу помочь?");
            return;
        }

        List<ChatGptClient.ChatMessage> messages = getChatMessages(reply, caption, userQuery);
        try {
            String answer = chat.chat(messages, 0.8, 600); // temperature и лимит токенов — можно подкрутить
            sendAnswer(entity, answer);
        } catch (Exception e) {
            e.printStackTrace();
            sendAnswer(entity, "Ой, тут что-то пошло не так… Давай попробуем ещё раз чуток позже?");
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
            // Ничего не шлём, чтобы не ловить TelegramApiValidationException
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
        // Режем по строкам/словам, чтобы не ломать середину
        List<String> parts = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            if (buf.length() + line.length() + 1 > limit) {
                if (!buf.isEmpty()) {
                    parts.add(buf.toString());
                    buf.setLength(0);
                }
                // если строка сама длиннее лимита — режем её по limit
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
}

