package ru.balladali.mashabot.core.services;

import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DialogMemoryStore {

    public record Turn(String role, String content) {}

    private static final Map<String, Deque<Turn>> DIALOG_MEMORY = new ConcurrentHashMap<>();
    private static final Map<String, Long> DIALOG_LAST_ACTIVITY = new ConcurrentHashMap<>();

    public static String keyFromMessage(Message message) {
        if (message == null) return "unknown";
        Long chatId = message.getChatId();
        Long userId = message.getFrom() != null ? message.getFrom().getId() : null;
        return String.valueOf(chatId) + ":" + String.valueOf(userId);
    }

    public static List<Turn> getHistory(String key, long ttlMs) {
        long now = System.currentTimeMillis();
        Long last = DIALOG_LAST_ACTIVITY.get(key);
        if (last != null && now - last > ttlMs) {
            DIALOG_MEMORY.remove(key);
            DIALOG_LAST_ACTIVITY.remove(key);
            return List.of();
        }
        Deque<Turn> history = DIALOG_MEMORY.get(key);
        if (history == null || history.isEmpty()) return List.of();
        return new ArrayList<>(history);
    }

    public static void append(String key, String role, String content, int memoryMessages, long ttlMs) {
        if (content == null || content.isBlank()) return;

        Deque<Turn> history = new ArrayDeque<>(getHistory(key, ttlMs));
        history.addLast(new Turn(role, content));

        while (history.size() > Math.max(1, memoryMessages)) {
            history.removeFirst();
        }

        DIALOG_MEMORY.put(key, history);
        DIALOG_LAST_ACTIVITY.put(key, System.currentTimeMillis());
    }
}
