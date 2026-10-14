package ru.balladali.mashabot.core.handlers.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import ru.balladali.mashabot.core.config.ChatGptProperties;
import ru.balladali.mashabot.core.config.MashaProperties;
import ru.balladali.mashabot.core.config.HandlerConfig;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;
import ru.balladali.mashabot.core.services.KeyedTaskExecutor;
import ru.balladali.mashabot.core.services.ProfileSummaryService;
import ru.balladali.mashabot.telegram.TelegramDelivery;
import ru.balladali.mashabot.telegram.TelegramMessage;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GptConversationHandlerTest {
    @TempDir Path memoryDir;

    @ParameterizedTest
    @ValueSource(ints = {1500, 2300})
    void handlerFactoryPassesConfiguredReplyBudgetToGpt(int maxTokens) throws Exception {
        ChatGptClient chat = mock(ChatGptClient.class);
        when(chat.chat(anyList(), anyDouble(), anyInt())).thenReturn("answer");
        ChatGptProperties properties = new ChatGptProperties("http://127.0.0.1", "test-only", "test-model",
                false, maxTokens, 120, 20, 30, 100, memoryDir.toString());
        try (KeyedTaskExecutor summaries = new KeyedTaskExecutor("test-summary", 1, 8, 1)) {
            GptConversationHandler handler = new HandlerConfig().gptConversationHandler(chat,
                    new MashaProperties("persona"), properties, summaries, mock(TelegramDelivery.class));
            handler.handle(entity("Маша привет"));
            verify(chat).chat(anyList(), eq(0.8), eq(maxTokens));
        }
    }

    @Test
    void slowSummaryDoesNotBlockAnswersOrEraseNewMessages() throws Exception {
        ChatGptClient chat = mock(ChatGptClient.class);
        ProfileSummaryService profiles = mock(ProfileSummaryService.class);
        TelegramDelivery delivery = mock(TelegramDelivery.class);
        when(profiles.loadForPrompt(anyString(), anyInt())).thenReturn("");
        when(chat.chat(anyList(), anyDouble(), anyInt())).thenReturn("answer");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(profiles.generateDialogSummary(anyString())).thenAnswer(invocation -> {
            entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return "summary";
        });
        try (KeyedTaskExecutor summaries = new KeyedTaskExecutor("test-summary", 1, 8, 1)) {
            try {
                GptConversationHandler handler = new GptConversationHandler(chat, "persona", 1500, 20, 30, 1, profiles, summaries, delivery);
                handler.handle(entity("Маша первый"));
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                handler.handle(entity("Маша второй")); // Must return while summary is blocked.
                verify(delivery, times(2)).send(any(), any());
                release.countDown();
                awaitIdle(summaries);
                handler.handle(entity("Маша третий"));
                awaitIdle(summaries);
                var transcripts = org.mockito.ArgumentCaptor.forClass(String.class);
                verify(profiles, times(2)).generateDialogSummary(transcripts.capture());
                assertFalse(transcripts.getAllValues().get(1).contains("первый"));
                assertTrue(transcripts.getAllValues().get(1).contains("второй"));
                assertTrue(transcripts.getAllValues().get(1).contains("третий"));
            } finally { release.countDown(); }
        }
    }

    @Test
    void failedSummaryRetainsItsSnapshotForTheNextAttempt() throws Exception {
        ChatGptClient chat = mock(ChatGptClient.class);
        ProfileSummaryService profiles = mock(ProfileSummaryService.class);
        when(profiles.loadForPrompt(anyString(), anyInt())).thenReturn("");
        when(chat.chat(anyList(), anyDouble(), anyInt())).thenReturn("answer");
        when(profiles.generateDialogSummary(anyString())).thenThrow(new IllegalStateException()).thenReturn("summary");
        try (KeyedTaskExecutor summaries = new KeyedTaskExecutor("test-summary", 1, 8, 1)) {
            GptConversationHandler handler = new GptConversationHandler(chat, "persona", 1500, 20, 30, 1, profiles, summaries, mock(TelegramDelivery.class));
            handler.handle(entity("Маша первый"));
            awaitIdle(summaries);
            handler.handle(entity("Маша второй"));
            awaitIdle(summaries);
            var transcripts = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(profiles, times(2)).generateDialogSummary(transcripts.capture());
            assertTrue(transcripts.getAllValues().get(1).contains("первый"));
            assertTrue(transcripts.getAllValues().get(1).contains("второй"));
        }
    }

    @Test
    void undeliveredOrEmptyAnswerIsNotAddedToHistory() throws Exception {
        ChatGptClient chat = mock(ChatGptClient.class);
        ProfileSummaryService profiles = mock(ProfileSummaryService.class);
        TelegramDelivery delivery = mock(TelegramDelivery.class);
        when(profiles.loadForPrompt(anyString(), anyInt())).thenReturn("");
        when(chat.chat(anyList(), anyDouble(), anyInt())).thenReturn("lost answer", "", "delivered answer");
        doThrow(new TelegramDelivery.DeliveryException(new Exception())).doNothing().when(delivery).send(any(), any());
        try (KeyedTaskExecutor summaries = new KeyedTaskExecutor("test-summary", 1, 8, 1)) {
            GptConversationHandler handler = new GptConversationHandler(chat, "persona", 1500, 20, 30, 100, profiles, summaries, delivery);
            assertThrows(TelegramDelivery.DeliveryException.class, () -> handler.handle(entity("Маша первый")));
            assertThrows(IllegalStateException.class, () -> handler.handle(entity("Маша второй")));
            handler.handle(entity("Маша третий"));
            org.mockito.ArgumentCaptor<List<ChatGptClient.ChatMessage>> requests = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(chat, times(3)).chat(requests.capture(), anyDouble(), anyInt());
            assertEquals(2, requests.getAllValues().get(2).size()); // Persona and current user only.
            verify(delivery, times(2)).send(any(), any());
        }
    }

    private TelegramMessage entity(String text) {
        Message message = mock(Message.class);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(1L);
        when(message.getMessageId()).thenReturn(10);
        return new TelegramMessage(message, mock(TelegramClient.class));
    }

    private void awaitIdle(KeyedTaskExecutor executor) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (executor.outstanding() > 0 && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals(0, executor.outstanding());
    }
}
