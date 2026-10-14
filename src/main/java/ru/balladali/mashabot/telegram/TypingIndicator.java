package ru.balladali.mashabot.telegram;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Best-effort status updates never occupy a message-processing worker. */
@Component
public class TypingIndicator {
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1,
            Thread.ofPlatform().name("typing-", 0).factory());

    public TypingIndicator() { scheduler.setRemoveOnCancelPolicy(true); }

    public AutoCloseable start(TelegramClient client, String chat) {
        AtomicBoolean stopped = new AtomicBoolean();
        var future = scheduler.scheduleWithFixedDelay(() -> {
            if (stopped.get()) return;
            try { client.execute(new SendChatAction(chat, ActionType.TYPING.toString())); }
            catch (Exception e) { LoggerFactory.getLogger(TypingIndicator.class).debug("event=typing_failed type={}", e.getClass().getSimpleName()); }
        }, 0, 4, TimeUnit.SECONDS);
        return () -> { stopped.set(true); future.cancel(false); };
    }

    @PreDestroy
    public void close() { scheduler.shutdownNow(); }
}
