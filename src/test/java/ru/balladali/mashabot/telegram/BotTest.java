package ru.balladali.mashabot.telegram;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.balladali.mashabot.core.handlers.message.MessageHandler;
import ru.balladali.mashabot.core.services.KeyedTaskExecutor;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BotTest {
    @Test
    void differentUsersInOneGroupRunInParallelButOneUsersMessagesStayOrdered() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), otherUser = new CountDownLatch(1);
        List<Integer> order = new java.util.concurrent.CopyOnWriteArrayList<>();
        MessageHandler handler = mock(MessageHandler.class);
        when(handler.needHandle(any())).thenReturn(true);
        doAnswer(call -> {
            TelegramMessage entity = call.getArgument(0);
            int id = entity.getMessage().getMessageId();
            if (id == 1) { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); }
            if (id == 3) otherUser.countDown();
            order.add(id);
            return null;
        }).when(handler).handle(any());
        try (KeyedTaskExecutor messages = new KeyedTaskExecutor("test", 2, 8, 4);
             KeyedTaskExecutor notices = new KeyedTaskExecutor("test-notice", 1, 8, 1)) {
            try {
                Bot bot = new Bot(mock(TelegramClient.class), List.of(handler), "", messages, notices,
                        mock(TelegramDelivery.class), mock(TypingIndicator.class));
                Update first = userUpdate(1, 10), second = userUpdate(2, 10), third = userUpdate(3, 20);
                bot.consume(List.of(first));
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                bot.consume(List.of(second, third));
                assertTrue(otherUser.await(3, TimeUnit.SECONDS));
                assertFalse(order.contains(2));
            } finally { release.countDown(); }
        }
        assertTrue(order.indexOf(1) < order.indexOf(2));
    }

    @Test
    void pollingReturnsWhileHandlerWorksAndErrorsReachUser() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), delivered = new CountDownLatch(1);
        MessageHandler handler = mock(MessageHandler.class);
        when(handler.needHandle(any())).thenReturn(true);
        doAnswer(call -> {
            entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); throw new IllegalStateException("secret upstream body");
        }).when(handler).handle(any());
        TelegramDelivery delivery = mock(TelegramDelivery.class);
        doAnswer(call -> { delivered.countDown(); return null; }).when(delivery).send(any(), any());
        try (KeyedTaskExecutor messages = new KeyedTaskExecutor("test", 2, 8, 4);
             KeyedTaskExecutor notices = new KeyedTaskExecutor("test-notice", 1, 8, 1)) {
            try {
                Bot bot = new Bot(mock(TelegramClient.class), List.of(handler), "", messages, notices, delivery, mock(TypingIndicator.class));
                bot.consume(List.of(update()));
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertEquals(1L, delivered.getCount());
                release.countDown();
                assertTrue(delivered.await(3, TimeUnit.SECONDS));
                var notice = org.mockito.ArgumentCaptor.forClass(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class);
                verify(delivery).send(any(), notice.capture());
                assertFalse(notice.getValue().getText().contains("secret"));
                assertTrue(notice.getValue().getText().contains("Не удалось"));
            } finally { release.countDown(); }
        }
    }

    @Test
    void fullQueueProducesBusyNoticeWithoutCallingHandler() throws Exception {
        CountDownLatch release = new CountDownLatch(1), entered = new CountDownLatch(1), delivered = new CountDownLatch(1);
        TelegramDelivery delivery = mock(TelegramDelivery.class);
        doAnswer(call -> { delivered.countDown(); return null; }).when(delivery).send(any(), any());
        MessageHandler handler = mock(MessageHandler.class);
        try (KeyedTaskExecutor messages = new KeyedTaskExecutor("test", 1, 1, 1);
             KeyedTaskExecutor notices = new KeyedTaskExecutor("test-notice", 1, 8, 1)) {
            try {
                messages.submit("occupied", () -> {
                    entered.countDown();
                    try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { throw new IllegalStateException(e); }
                });
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                new Bot(mock(TelegramClient.class), List.of(handler), "", messages, notices, delivery, mock(TypingIndicator.class)).consume(List.of(update()));
                assertTrue(delivered.await(3, TimeUnit.SECONDS));
                verifyNoInteractions(handler);
            } finally { release.countDown(); }
        }
    }

    private Update update() {
        Message message = mock(Message.class);
        when(message.getChatId()).thenReturn(1L);
        when(message.getText()).thenReturn("Маша привет");
        Update update = new Update(); update.setUpdateId(123); update.setMessage(message);
        return update;
    }
    private Update userUpdate(int messageId, long userId) {
        Update update = update();
        var user = mock(org.telegram.telegrambots.meta.api.objects.User.class);
        when(user.getId()).thenReturn(userId);
        when(update.getMessage().getFrom()).thenReturn(user);
        when(update.getMessage().getMessageId()).thenReturn(messageId);
        update.setUpdateId(messageId);
        return update;
    }
}
