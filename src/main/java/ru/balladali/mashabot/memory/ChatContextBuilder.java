package ru.balladali.mashabot.memory;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;

import java.util.*;

@Component
public class ChatContextBuilder {
    private final ChatMemoryService memory;
    private final MemoryProperties p;

    public ChatContextBuilder(ChatMemoryService memory, MemoryProperties p) {
        this.memory = memory;
        this.p = p;
    }

    public boolean enabled() {
        return memory.enabled();
    }

    public List<ChatGptClient.ChatMessage> build(Message current) {
        var now = ChatMessage.from(current, false);
        Map<Long, ChatMessage> selected = new LinkedHashMap<>();
        selected.put(now.messageId(), now);
        try {
            Long parent = now.replyId();
            Set<Long> visited = new HashSet<>();
            visited.add(now.messageId());
            for (int i = 0;
                    parent != null && i < p.getContext().getReplyDepth() && visited.add(parent);
                    i++) {
                var found = memory.find(now.chatId(), parent).orElse(null);
                if (found == null
                        && current.getReplyToMessage() != null
                        && current.getReplyToMessage().getMessageId().longValue() == parent
                        && Objects.equals(
                                current.getChatId(), current.getReplyToMessage().getChatId()))
                    found = ChatMessage.from(current.getReplyToMessage(), false);
                if (found == null) break;
                selected.put(found.messageId(), found);
                parent = found.replyId();
            }
            for (var item :
                    memory.recent(now.chatId(), now.threadId(), p.getContext().getRecentLimit()))
                selected.putIfAbsent(item.messageId(), item);
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass())
                    .warn("event=memory_context_error type={}", e.getClass().getSimpleName());
        }
        List<ChatMessage> kept = new ArrayList<>();
        int remaining = p.getContext().getMaxCharacters();
        for (var item : selected.values()) {
            if (kept.size() >= p.getContext().getMaxMessages()) break;
            int size = item.formatted().length();
            if (size <= remaining) {
                kept.add(item);
                remaining -= size;
            } else if (kept.isEmpty()) {
                String text = item.text() == null ? "" : item.text();
                int budget = Math.max(0, remaining - (size - text.length()) - 1);
                if (budget > 0
                        && budget < text.length()
                        && Character.isHighSurrogate(text.charAt(budget - 1))) budget--;
                kept.add(
                        new ChatMessage(
                                item.chatId(),
                                item.messageId(),
                                item.threadId(),
                                item.userId(),
                                item.username(),
                                item.displayName(),
                                item.bot(),
                                item.direction(),
                                item.replyId(),
                                item.type(),
                                text.substring(0, Math.min(text.length(), budget)) + "…",
                                item.date(),
                                item.edited()));
                remaining = 0;
            }
        }
        kept.sort(
                Comparator.comparing(ChatMessage::date).thenComparingLong(ChatMessage::messageId));
        return kept.stream()
                .map(
                        x ->
                                new ChatGptClient.ChatMessage(
                                        x.direction().equals("OUTGOING") ? "assistant" : "user",
                                        x.formatted()))
                .toList();
    }
}
