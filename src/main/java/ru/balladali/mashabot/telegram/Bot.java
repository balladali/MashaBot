package ru.balladali.mashabot.telegram;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.balladali.mashabot.core.handlers.message.MessageHandler;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class Bot implements LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final List<MessageHandler> messageHandlers;
    private final ThreadPoolExecutor executor;

    private final Counter updatesReceived;
    private final Counter updatesDropped;
    private final Timer updateHandleTimer;

    @Autowired
    public Bot(TelegramClient telegramClient,
               List<MessageHandler> messageHandlers,
               MeterRegistry meterRegistry,
               @Value("${bot.update-workers:4}") int workers,
               @Value("${bot.update-queue-capacity:200}") int queueCapacity) {
        this.telegramClient = telegramClient;
        this.messageHandlers = messageHandlers;

        int poolSize = Math.max(1, workers);
        int queueSize = Math.max(10, queueCapacity);

        this.executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                30,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.updatesReceived = meterRegistry.counter("masha.telegram.updates.received");
        this.updatesDropped = meterRegistry.counter("masha.telegram.updates.dropped");
        this.updateHandleTimer = Timer.builder("masha.telegram.update.handle")
                .description("Time spent handling telegram updates")
                .register(meterRegistry);

        meterRegistry.gauge("masha.telegram.executor.queue.size", executor.getQueue(), java.util.Queue::size);
        meterRegistry.gauge("masha.telegram.executor.active", executor, ThreadPoolExecutor::getActiveCount);
    }

    private void handleMessage(Message message) {
        TelegramMessage messageEntity = new TelegramMessage(message, this.telegramClient);
        for (MessageHandler messageHandler : messageHandlers) {
            if (messageHandler.needHandle(messageEntity)) {
                messageHandler.handle(messageEntity);
                return;
            }
        }
    }

    @Override
    public void consume(Update update) {
        updatesReceived.increment();

        Message message = update.getMessage();
        if (message == null) {
            return;
        }

        try {
            executor.execute(() -> updateHandleTimer.record(() -> handleMessage(message)));
        } catch (RuntimeException e) {
            updatesDropped.increment();
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
