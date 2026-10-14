package ru.balladali.mashabot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class TelegramDelivery {
    private static final Logger log = LoggerFactory.getLogger(TelegramDelivery.class);
    private final Sleeper sleeper;
    @FunctionalInterface
    interface Sleeper { void sleep(long millis) throws InterruptedException; }
    public TelegramDelivery() { this(Thread::sleep); }
    TelegramDelivery(Sleeper sleeper) { this.sleeper = sleeper; }

    public void send(TelegramClient client, SendMessage message) {
        long start = System.nanoTime();
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                client.execute(message);
                log.info("event=telegram_send status=ok attempt={} duration_ms={}", attempt,
                        (System.nanoTime() - start) / 1_000_000);
                return;
            } catch (TelegramApiException e) {
                int code = e instanceof TelegramApiRequestException api && api.getErrorCode() != null ? api.getErrorCode() : 0;
                long delay = 500L * attempt;
                if (e instanceof TelegramApiRequestException api && code == 429) {
                    if (api.getParameters() == null || api.getParameters().getRetryAfter() == null) throw new DeliveryException(e);
                    delay = Math.max(1000L, api.getParameters().getRetryAfter().longValue() * 1000);
                }
                // Transport errors can occur after delivery: do not blindly resend them.
                if (attempt == 3 || !(code == 429 || code >= 500 && code <= 599) || delay > 10_000) {
                    log.warn("event=telegram_send status=failed code={} attempt={}", code, attempt);
                    throw new DeliveryException(e);
                }
                log.warn("event=telegram_retry code={} attempt={} delay_ms={}", code, attempt, delay);
                try { sleeper.sleep(delay); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new DeliveryException(interrupted);
                }
            }
        }
    }
    public static class DeliveryException extends RuntimeException {
        public DeliveryException(Exception cause) { super("Telegram delivery failed", cause); }
    }
}
