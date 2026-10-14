package ru.balladali.mashabot.telegram;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.ResponseParameters;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramDeliveryTest {
    @Test
    void respectsRetryAfterThenSendsSuccessfully() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        SendMessage message = new SendMessage("1", "hello");
        TelegramApiRequestException limited = apiError(429, 2);
        when(client.execute(message)).thenThrow(limited).thenReturn(null);
        List<Long> delays = new ArrayList<>();
        new TelegramDelivery(delays::add).send(client, message);
        assertEquals(List.of(2000L), delays);
        verify(client, times(2)).execute(message);
    }

    @Test
    void retriesServerErrorsButStopsAfterThreeAttempts() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        SendMessage message = new SendMessage("1", "hello");
        TelegramApiRequestException error = apiError(502, null);
        when(client.execute(message)).thenThrow(error);
        List<Long> delays = new ArrayList<>();
        assertThrows(TelegramDelivery.DeliveryException.class, () -> new TelegramDelivery(delays::add).send(client, message));
        verify(client, times(3)).execute(message);
        assertEquals(List.of(500L, 1000L), delays);
    }

    @Test
    void doesNotRetryForbiddenTransportFailureOrExcessiveRetryAfter() throws Exception {
        for (TelegramApiException error : List.of(apiError(403, null), new TelegramApiException("transport"), apiError(429, 60))) {
            TelegramClient client = mock(TelegramClient.class);
            SendMessage message = new SendMessage("1", "hello");
            when(client.execute(message)).thenThrow(error);
            assertThrows(TelegramDelivery.DeliveryException.class,
                    () -> new TelegramDelivery(delay -> fail("Must not sleep")).send(client, message));
            verify(client).execute(message);
        }
    }

    private TelegramApiRequestException apiError(int code, Integer retryAfter) {
        TelegramApiRequestException error = mock(TelegramApiRequestException.class);
        when(error.getErrorCode()).thenReturn(code);
        when(error.getParameters()).thenReturn(new ResponseParameters(null, retryAfter));
        return error;
    }
}
