package ru.balladali.mashabot.core.handlers.message;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.balladali.mashabot.telegram.TelegramDelivery;
import ru.balladali.mashabot.telegram.TelegramMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalculatorHandlerTest {
    private final TelegramDelivery delivery = mock(TelegramDelivery.class);
    private final CalculatorHandler handler = new CalculatorHandler(delivery);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2*2|2 * 2 = 4", "10/5|10 / 5 = 2", "10+2|10 + 2 = 12",
            "60-4|60 - 4 = 56", " 10 + 2 = |10 + 2 = 12",
            "1,5×2?|1.5 × 2 = 3", "-2*-3|-2 * -3 = 6",
            "10--2|10 - -2 = 12", "0.1+0.2|0.1 + 0.2 = 0.3",
            "1/3|1 / 3 ≈ 0.3333333333333333", "1÷8|1 ÷ 8 = 0.125",
            "10−4|10 − 4 = 6", "1/0|На ноль делить нельзя.",
            "0/-0,0|На ноль делить нельзя."
    })
    void calculatesAndRepliesToOriginalMessage(String formula, String answer) {
        TelegramMessage message = message(formula);
        assertTrue(handler.needHandle(message));
        handler.handle(message);
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(delivery).send(isNull(), sent.capture());
        assertEquals(answer, sent.getValue().getText());
        assertEquals(123, sent.getValue().getReplyToMessageId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"привет", "посчитай 2*2", "встреча 10/5", "12.11.2026",
            "2026-11-12", "+79991234567", "2+2=4", "2+", "2**2", "2+2 текст",
            "https://example.com/10/5", "2+3*4", "1e3+2", "100 рублей", "123",
            "1 000+2", "2\nпривет", "1234567890123456789012345678901+1"})
    void ignoresMessagesThatAreNotStandaloneExpressions(String text) {
        TelegramMessage message = message(text);
        assertFalse(handler.needHandle(message));
        handler.handle(message);
        verifyNoInteractions(delivery);
    }

    private TelegramMessage message(String text) {
        Message raw = new Message();
        raw.setText(text);
        raw.setMessageId(123);
        return new TelegramMessage(raw, null) {
            @Override public String getChatId() { return "42"; }
        };
    }
}
