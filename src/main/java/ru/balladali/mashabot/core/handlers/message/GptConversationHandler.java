package ru.balladali.mashabot.core.handlers.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;
import ru.balladali.mashabot.core.services.KeyedTaskExecutor;
import ru.balladali.mashabot.core.services.ProfileSummaryService;
import ru.balladali.mashabot.telegram.TelegramDelivery;
import ru.balladali.mashabot.telegram.TelegramMessage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class GptConversationHandler implements MessageHandler {
    private static final Logger log = LoggerFactory.getLogger(GptConversationHandler.class);
    private static final int TG_LIMIT = 4096;
    private static final int LONG_MEMORY_PROMPT_LIMIT = 2500;
    private static final Pattern TRIGGER = Pattern.compile("^(?:маша[\\s,:-]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final ChatGptClient chat;
    private final String personaSystemPrompt;
    private final int maxTokens;
    private final int memoryMessages;
    private final long memoryTtlMs;
    private final int summaryEveryUserMessages;
    private final ProfileSummaryService profiles;
    private final KeyedTaskExecutor summaries;
    private final TelegramDelivery delivery;
    private final Map<String, DialogState> dialogs = new ConcurrentHashMap<>();
    private volatile Long botId;

    private record Entry(long sequence, ChatGptClient.ChatMessage message) {}
    private static final class DialogState {
        final Deque<ChatGptClient.ChatMessage> history = new ArrayDeque<>();
        final Deque<Entry> summary = new ArrayDeque<>();
        long lastActivity;
        long sequence;
        int userMessages;
        boolean summaryPending;
    }

    public GptConversationHandler(ChatGptClient chat, String personaSystemPrompt, int maxTokens, int memoryMessages,
                                  int memoryTtlMinutes, int summaryEveryUserMessages, String profileDir,
                                  KeyedTaskExecutor summaries, TelegramDelivery delivery) {
        this(chat, personaSystemPrompt, maxTokens, memoryMessages, memoryTtlMinutes, summaryEveryUserMessages,
                new ProfileSummaryService(chat, profileDir), summaries, delivery);
    }

    public GptConversationHandler(ChatGptClient chat, String personaSystemPrompt, int maxTokens, int memoryMessages,
                                  int memoryTtlMinutes, int summaryEveryUserMessages, ProfileSummaryService profiles,
                                  KeyedTaskExecutor summaries, TelegramDelivery delivery) {
        this.chat = chat;
        this.personaSystemPrompt = personaSystemPrompt;
        if (maxTokens < 1) throw new IllegalArgumentException("maxTokens must be positive");
        this.maxTokens = maxTokens;
        this.memoryMessages = Math.max(1, memoryMessages);
        this.memoryTtlMs = Math.max(1, memoryTtlMinutes) * 60_000L;
        this.summaryEveryUserMessages = Math.max(1, summaryEveryUserMessages);
        this.profiles = profiles;
        this.summaries = summaries;
        this.delivery = delivery;
    }

    @Override
    public boolean needHandle(TelegramMessage entity) {
        if (entity == null) return false;
        String text = entity.getText();
        if (text != null && TRIGGER.matcher(text).find()) return true;
        Message message = entity.getMessage();
        if (message == null || message.getReplyToMessage() == null || message.getReplyToMessage().getFrom() == null) return false;
        if (botId == null) {
            try { botId = entity.getClient().execute(new GetMe()).getId(); }
            catch (Exception e) {
                log.warn("event=bot_identity_failed type={}", e.getClass().getSimpleName());
                return false;
            }
        }
        return Objects.equals(message.getReplyToMessage().getFrom().getId(), botId);
    }

    @Override
    public void handle(TelegramMessage entity) {
        String query = TRIGGER.matcher(Optional.ofNullable(entity.getText()).orElse("")).replaceFirst("").trim();
        Message replyMessage = entity.getMessage().getReplyToMessage();
        String reply = replyMessage == null ? null : replyMessage.getText();
        String caption = replyMessage == null ? null : replyMessage.getCaption();
        if (query.isBlank() && (reply == null || reply.isBlank()) && (caption == null || caption.isBlank())) {
            sendAnswer(entity, "Привет! Я Маша 🦄 Чем могу помочь?");
            return;
        }

        String key = entity.getChatId() + ":" + (entity.getMessage().getFrom() == null ? "unknown" : entity.getMessage().getFrom().getId());
        DialogState state = dialogs.computeIfAbsent(key, ignored -> new DialogState());
        List<ChatGptClient.ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatGptClient.ChatMessage("system", personaSystemPrompt));
        String longMemory = profiles.loadForPrompt(key, LONG_MEMORY_PROMPT_LIMIT);
        if (!longMemory.isBlank()) messages.add(new ChatGptClient.ChatMessage("system", "Долговременная память о пользователе:\n" + longMemory));
        synchronized (state) {
            if (System.currentTimeMillis() - state.lastActivity > memoryTtlMs) state.history.clear();
            messages.addAll(state.history);
        }
        String context = (reply == null ? "" : reply) + (caption == null ? "" : "\n" + caption);
        if (!context.isBlank()) messages.add(new ChatGptClient.ChatMessage("system", "Контекст сообщения: «" + context + "»."));
        messages.add(new ChatGptClient.ChatMessage("user", query));
        String answer;
        try {
            answer = chat.chat(messages, 0.8, maxTokens);
            if (answer == null || answer.isBlank()) throw new IllegalStateException("Empty completion response");
        } catch (Exception e) {
            // Do not print upstream bodies, prompts or credentials from exception messages.
            throw new IllegalStateException("Conversation request failed", e);
        }
        sendAnswer(entity, answer); // Delivery errors propagate; do not remember an undelivered answer.
        synchronized (state) {
            append(state, "user", query);
            append(state, "assistant", answer);
            state.lastActivity = System.currentTimeMillis();
            state.userMessages++;
            scheduleSummary(key, state);
        }
    }

    private void append(DialogState state, String role, String text) {
        ChatGptClient.ChatMessage message = new ChatGptClient.ChatMessage(role, text);
        state.history.addLast(message);
        while (state.history.size() > memoryMessages) state.history.removeFirst();
        state.summary.addLast(new Entry(++state.sequence, message));
        while (state.summary.size() > Math.max(240L, 2L * summaryEveryUserMessages)) state.summary.removeFirst();
    }

    // Called under the dialog lock. Background completion only acknowledges its own snapshot.
    private void scheduleSummary(String key, DialogState state) {
        if (state.summaryPending || state.userMessages < summaryEveryUserMessages || state.summary.isEmpty()) return;
        List<Entry> snapshot = List.copyOf(state.summary);
        long through = snapshot.getLast().sequence();
        int userCount = state.userMessages;
        String requestId = MDC.get("requestId");
        state.summaryPending = true;
        boolean accepted = summaries.submit(key, () -> {
            long start = System.nanoTime();
            try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
                StringBuilder transcript = new StringBuilder();
                for (Entry entry : snapshot) transcript.append(entry.message().role()).append(": ").append(entry.message().content()).append("\n");
                String summary = profiles.generateDialogSummary(transcript.toString());
                if (summary == null || summary.isBlank()) throw new IllegalStateException("Empty summary");
                profiles.appendSummaryAndMaybeResummarize(key, summary);
                synchronized (state) {
                    state.summary.removeIf(entry -> entry.sequence() <= through);
                    state.userMessages -= userCount;
                }
                log.info("event=summary_finished duration_ms={}", (System.nanoTime() - start) / 1_000_000);
            } catch (Exception e) {
                log.warn("event=summary_failed type={} duration_ms={}", e.getClass().getSimpleName(), (System.nanoTime() - start) / 1_000_000);
            } finally {
                synchronized (state) { state.summaryPending = false; }
            }
        });
        if (!accepted) {
            state.summaryPending = false;
            log.warn("event=summary_deferred");
        }
    }

    @Override
    public void sendAnswer(TelegramMessage entity, String answer) {
        if (answer == null || answer.isBlank()) throw new IllegalArgumentException("Empty answer");
        String text = answer.strip();
        boolean first = true;
        for (int start = 0; start < text.length();) {
            int end = Math.min(text.length(), start + TG_LIMIT);
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            SendMessage message = new SendMessage(entity.getChatId(), text.substring(start, end));
            if (first) message.setReplyToMessageId(entity.getMessage().getMessageId());
            delivery.send(entity.getClient(), message);
            first = false;
            start = end;
        }
    }

}
