package ru.balladali.mashabot.core.handlers.message;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.balladali.mashabot.core.services.ProfileSummaryService;
import ru.balladali.mashabot.telegram.TelegramMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class GptConversationHandler implements MessageHandler {
    private final ChatClient chatClient;
    private final String model;
    private final String personaSystemPrompt;
    private final int memoryMessages;
    private final long memoryTtlMs;
    private final int summaryEveryUserMessages;
    private final ProfileSummaryService profileSummaryService;

    private static final int TG_LIMIT = 4096;
    private static final int LONG_MEMORY_PROMPT_LIMIT = 2500;
    private static final Pattern TRIGGER = Pattern.compile("^(?:маша[\\s,:-]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Map<String, Deque<DialogMessage>> DIALOG_MEMORY = new ConcurrentHashMap<>();
    private static final Map<String, Long> DIALOG_LAST_ACTIVITY = new ConcurrentHashMap<>();
    private static final Map<String, Deque<DialogMessage>> SUMMARY_BUFFER = new ConcurrentHashMap<>();
    private static final Map<String, Integer> USER_MESSAGES_SINCE_SUMMARY = new ConcurrentHashMap<>();
    private static volatile Long BOT_ID = null;

    public GptConversationHandler(ChatClient chatClient,
                                  String model,
                                  String personaSystemPrompt,
                                  int memoryMessages,
                                  int memoryTtlMinutes,
                                  int summaryEveryUserMessages,
                                  ProfileSummaryService profileSummaryService) {
        this.chatClient = chatClient;
        this.model = model;
        this.personaSystemPrompt = personaSystemPrompt;
        this.memoryMessages = Math.max(1, memoryMessages);
        this.memoryTtlMs = Math.max(1, memoryTtlMinutes) * 60_000L;
        this.summaryEveryUserMessages = Math.max(1, summaryEveryUserMessages);
        this.profileSummaryService = profileSummaryService;
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

        String memoryKey = buildMemoryKey(entity.getMessage());
        List<DialogMessage> messages = getChatMessages(memoryKey, reply, caption, userQuery);
        try {
            sendTyping(entity);

            String answer = callModel(messages, 0.8, 600);
            sendAnswer(entity, answer);

            appendMemory(memoryKey, "user", userQuery);
            appendMemory(memoryKey, "assistant", answer);

            appendSummaryBuffer(memoryKey, "user", userQuery);
            appendSummaryBuffer(memoryKey, "assistant", answer);

            if (needSummary(memoryKey)) {
                summarizeAndPersist(memoryKey);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendAnswer(entity, "Ой, тут что-то пошло не так… Давай попробуем ещё раз чуток позже?");
        }
    }

    private String callModel(List<DialogMessage> messages, double temperature, int maxTokens) {
        List<org.springframework.ai.chat.messages.Message> aiMessages = new ArrayList<>();
        for (DialogMessage message : messages) {
            switch (message.role()) {
                case "system" -> aiMessages.add(new SystemMessage(message.content()));
                case "assistant" -> aiMessages.add(new AssistantMessage(message.content()));
                default -> aiMessages.add(new UserMessage(message.content()));
            }
        }

        return chatClient.prompt()
                .messages(aiMessages)
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .build())
                .call()
                .content();
    }

    private String buildMemoryKey(Message message) {
        if (message == null) return "unknown";
        Long chatId = message.getChatId();
        Long userId = message.getFrom() != null ? message.getFrom().getId() : null;
        return String.valueOf(chatId) + ":" + String.valueOf(userId);
    }

    private Deque<DialogMessage> getActiveHistory(String key) {
        long now = System.currentTimeMillis();
        Long last = DIALOG_LAST_ACTIVITY.get(key);
        if (last != null && now - last > memoryTtlMs) {
            DIALOG_MEMORY.remove(key);
            DIALOG_LAST_ACTIVITY.remove(key);
            return new ArrayDeque<>();
        }
        return DIALOG_MEMORY.getOrDefault(key, new ArrayDeque<>());
    }

    private void appendMemory(String key, String role, String content) {
        if (content == null || content.isBlank()) return;

        Deque<DialogMessage> history = new ArrayDeque<>(getActiveHistory(key));
        history.addLast(new DialogMessage(role, content));

        while (history.size() > memoryMessages) {
            history.removeFirst();
        }

        DIALOG_MEMORY.put(key, history);
        DIALOG_LAST_ACTIVITY.put(key, System.currentTimeMillis());
    }

    private void appendSummaryBuffer(String key, String role, String content) {
        if (content == null || content.isBlank()) return;

        Deque<DialogMessage> buffer = new ArrayDeque<>(SUMMARY_BUFFER.getOrDefault(key, new ArrayDeque<>()));
        buffer.addLast(new DialogMessage(role, content));

        while (buffer.size() > 240) {
            buffer.removeFirst();
        }

        SUMMARY_BUFFER.put(key, buffer);

        if ("user".equals(role)) {
            USER_MESSAGES_SINCE_SUMMARY.merge(key, 1, Integer::sum);
        }
    }

    private boolean needSummary(String key) {
        return USER_MESSAGES_SINCE_SUMMARY.getOrDefault(key, 0) >= summaryEveryUserMessages;
    }

    private void summarizeAndPersist(String key) {
        Deque<DialogMessage> buffer = SUMMARY_BUFFER.getOrDefault(key, new ArrayDeque<>());
        if (buffer.isEmpty()) return;

        StringBuilder transcript = new StringBuilder();
        for (DialogMessage m : buffer) {
            transcript.append(m.role()).append(": ").append(m.content()).append("\n");
        }

        try {
            String summary = profileSummaryService.generateDialogSummary(transcript.toString());
            profileSummaryService.appendSummaryAndMaybeResummarize(key, summary);
            USER_MESSAGES_SINCE_SUMMARY.put(key, 0);
            SUMMARY_BUFFER.remove(key);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NotNull
    private List<DialogMessage> getChatMessages(String memoryKey, String reply, String caption, String userQuery) {
        List<DialogMessage> messages = new ArrayList<>();
        messages.add(new DialogMessage("system", personaSystemPrompt));

        String longMemory = profileSummaryService.loadForPrompt(memoryKey, LONG_MEMORY_PROMPT_LIMIT);
        if (!longMemory.isBlank()) {
            messages.add(new DialogMessage("system", "Долговременная память о пользователе:\n" + longMemory));
        }

        Deque<DialogMessage> history = getActiveHistory(memoryKey);
        if (!history.isEmpty()) {
            messages.addAll(history);
        }

        String context = "";
        if (reply != null && !reply.isBlank()) {
            context += reply;
        }
        if (caption != null && !caption.isBlank()) {
            context += "\n" + caption;
        }
        if (!context.isBlank()) {
            messages.add(new DialogMessage("system", "Контекст сообщения, на которое пользователь ответил: «" + context + "»."));
        }

        messages.add(new DialogMessage("user", userQuery));
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

    private record DialogMessage(String role, String content) {}
}
