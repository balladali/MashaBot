package ru.balladali.mashabot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.balladali.mashabot.core.handlers.message.MessageHandler;
import ru.balladali.mashabot.core.services.KeyedTaskExecutor;
import java.util.List;

@Component
public class Bot implements LongPollingUpdateConsumer {
    private static final Logger log = LoggerFactory.getLogger(Bot.class);
    private final TelegramClient client;
    private final List<MessageHandler> handlers;
    private final String errorChatId;
    private final KeyedTaskExecutor messages;
    private final KeyedTaskExecutor notifications;
    private final TelegramDelivery delivery;
    private final TypingIndicator typing;

    public Bot(TelegramClient client, @Qualifier("messageHandlers") List<MessageHandler> handlers,
               @Value("${bot.error-chat-id:}") String errorChatId,
               @Qualifier("messageExecutor") KeyedTaskExecutor messages,
               @Qualifier("notificationExecutor") KeyedTaskExecutor notifications,
               TelegramDelivery delivery, TypingIndicator typing) {
        this.client = client;
        this.handlers = handlers;
        this.errorChatId = errorChatId;
        this.messages = messages;
        this.notifications = notifications;
        this.delivery = delivery;
        this.typing = typing;
    }

    @Override
    public void consume(List<Update> updates) {
        for (Update update : updates) {
            Message message = update.getMessage();
            if (message == null) continue;
            long received = System.nanoTime();
            String requestId = String.valueOf(update.getUpdateId());
            String chat = String.valueOf(message.getChatId());
            String dialog = chat + ":" + (message.getFrom() == null ? "unknown" : message.getFrom().getId());
            if (!messages.submit(dialog, () -> handle(message, requestId, received))) {
                log.warn("event=queue_rejected request={} outstanding={}", requestId, messages.outstanding());
                // A slow notification must never block polling.
                boolean queued = notifications.submit(chat, () -> sendNotice(chat,
                        "Сейчас много запросов. Пожалуйста, повтори сообщение чуть позже."));
                if (!queued) log.warn("event=busy_notice_dropped request={}", requestId);
            }
        }
    }

    private void handle(Message message, String requestId, long received) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            long start = System.nanoTime();
            log.info("event=message_started request={} queue_ms={}", requestId, (start - received) / 1_000_000);
            TelegramMessage entity = new TelegramMessage(message, client);
            try {
                for (MessageHandler handler : handlers) {
                    if (!handler.needHandle(entity)) continue;
                    log.info("event=handler_selected request={} handler={}", requestId, handler.getClass().getSimpleName());
                    try (AutoCloseable progress = typing.start(client, entity.getChatId())) {
                        handler.handle(entity);
                    }
                    log.info("event=message_finished request={} duration_ms={}", requestId, (System.nanoTime() - start) / 1_000_000);
                    return;
                }
                log.info("event=message_ignored request={}", requestId);
            } catch (Exception e) {
                log.warn("event=message_failed request={} type={} duration_ms={}", requestId,
                        e.getClass().getSimpleName(), (System.nanoTime() - start) / 1_000_000);
                if (!(e instanceof TelegramDelivery.DeliveryException)) {
                    sendNotice(entity.getChatId(), "Не удалось обработать запрос. Попробуй ещё раз чуть позже.");
                }
                if (errorChatId != null && !errorChatId.isBlank()) {
                    notifications.submit("errors", () -> sendNotice(errorChatId,
                            "MashaBot: ошибка запроса " + requestId + " (" + e.getClass().getSimpleName() + ")."));
                }
            }
        }
    }

    private void sendNotice(String chat, String text) {
        try {
            delivery.send(client, new SendMessage(chat, text));
        } catch (TelegramDelivery.DeliveryException e) {
            log.warn("event=notice_failed");
        }
    }
}
