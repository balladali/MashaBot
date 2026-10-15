package ru.balladali.mashabot.core.handlers.message;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import ru.balladali.mashabot.core.clients.exchange.ExchangeRateClient;
import ru.balladali.mashabot.telegram.TelegramDelivery;
import ru.balladali.mashabot.telegram.TelegramMessage;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrencyConvertHandlerTest {
    private final ExchangeRateClient rates = mock(ExchangeRateClient.class);
    private final TelegramDelivery delivery = mock(TelegramDelivery.class);
    private final CurrencyConvertHandler handler = new CurrencyConvertHandler(rates, delivery);

    static Stream<Arguments> currencyCodes() {
        return Stream.of("USD", "EUR", "GBP", "CHF", "JPY", "CNY", "PLN", "GEL", "KZT",
                "NOK", "SEK", "DKK", "CZK", "ISK", "BYN", "UAH", "AMD", "AZN", "KGS",
                "TJS", "TMT", "UZS", "MDL", "RON", "TRY", "AUD", "CAD")
                .flatMap(iso -> Stream.of("100 " + iso, iso + " 100", "100" + iso, iso + "100",
                                "100 " + iso.toLowerCase(java.util.Locale.ROOT))
                        .map(text -> Arguments.of(text, iso, "100")));
    }

    @ParameterizedTest
    @MethodSource("currencyCodes")
    void convertsKnownCurrencyCodes(String text, String iso, String amount) throws Exception {
        assertConversion(text, iso, amount);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "100 евро|EUR|100", "100 фунтов|GBP|100", "100 франков|CHF|100",
            "100 иен|JPY|100", "100 йен|JPY|100", "100 юаней|CNY|100",
            "100 злотых|PLN|100", "100 лари|GEL|100", "100 тенге|KZT|100",
            "100 норвежских крон|NOK|100", "100 шведских крон|SEK|100",
            "100 датских крон|DKK|100", "100 чешских крон|CZK|100", "100 исландских крон|ISK|100",
            "100 белорусских рублей|BYN|100", "100 гривен|UAH|100",
            "100 драмов|AMD|100", "100 азербайджанских манатов|AZN|100",
            "100 сомов|KGS|100", "100 сомони|TJS|100", "100 туркменских манатов|TMT|100",
            "100 сумов|UZS|100", "100 молдавских леев|MDL|100", "100 румынских леев|RON|100",
            "100 лир|TRY|100", "100 dollars|USD|100", "100 euros|EUR|100", "100 pounds|GBP|100",
            "100 армянских драмов|AMD|100", "100 киргизских сомов|KGS|100", "100 узбекских сумов|UZS|100",
            "100 китайских юаней|CNY|100", "100 американских долларов|USD|100",
            "100 канадских долларов|CAD|100", "100 австралийских долларов|AUD|100",
            "100 Canadian dollars|CAD|100", "100 Australian dollars|AUD|100",
            "100 ЕВРО|EUR|100", "100 ДОЛЛАРОВ|USD|100", "100 francs|CHF|100",
            "€100|EUR|100", "£100|GBP|100", "¥100|JPY|100", "₸100|KZT|100",
            "₺100|TRY|100", "₴100|UAH|100",
            "1 234,56 EUR|EUR|1234.56", "1234.56 EUR|EUR|1234.56",
            "1.234,56 EUR|EUR|1234.56", "1,234.56 EUR|EUR|1234.56",
            "1.234.567,89 EUR|EUR|1234567.89", "1,234,567.89 EUR|EUR|1234567.89",
            "1 234 567 EUR|EUR|1234567", "1.234.567 EUR|EUR|1234567",
            "1,234,567 EUR|EUR|1234567", "1 234,56 EUR|EUR|1234.56",
            "2к EUR|EUR|2000", "2k EUR|EUR|2000", "2кк EUR|EUR|2000000",
            "2kk EUR|EUR|2000000", "2 тыс EUR|EUR|2000", "2 млн EUR|EUR|2000000",
            "2 млрд EUR|EUR|2000000000", "EUR 2к|EUR|2000"
    })
    void convertsCurrencyNamesSymbolsAndAmounts(String text, String iso, String amount) throws Exception {
        assertConversion(text, iso, amount);
    }

    private void assertConversion(String text, String iso, String amount) throws Exception {
        when(rates.rateToRub(iso)).thenReturn(new BigDecimal("2"));
        BigDecimal expected = new BigDecimal(amount);
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String answer = df.format(expected) + " " + iso + " ≈ " + df.format(expected.multiply(new BigDecimal("2")))
                + " RUB (курс " + df.format(new BigDecimal("2")) + ")";
        for (TelegramMessage message : new TelegramMessage[]{entity(text, null), entity(null, text)}) {
            assertTrue(handler.needHandle(message), text);
            handler.handle(message);
        }
        verify(rates, times(2)).rateToRub(iso);
        verifyNoMoreInteractions(rates);
        verify(delivery, times(2)).send(isNull(), argThat(sent -> answer.equals(sent.getText())));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.instagram.com/reel/DefKAlqRK7Y/?xtok=MXlwczVrZjhhMDcz",
            "@st4sik", "@usd100", "https://example.com/100USD", "www.example.com/USD100",
            "100 ABC", "ABC 100", "100 XXX", "abc100USD", "100USDTsuffix", "100 рублей"
    })
    void ignoresLinksUsernamesAndNonCurrencyTokensInTextAndCaptions(String text) {
        for (TelegramMessage message : new TelegramMessage[]{entity(text, null), entity(null, text)}) {
            assertFalse(handler.needHandle(message), text);
            assertDoesNotThrow(() -> handler.handle(message));
        }
        verifyNoInteractions(rates, delivery);
    }

    @ParameterizedTest
    @ValueSource(strings = {"100 USD", "USD100", "100USD", "$100", "100 долларов",
            "цена: 100 USD!", "https://example.com/USD999 цена 100 USD", "@usd999 цена 100 USD"})
    void convertsRealAmountsIncludingThoseNextToLinksAndUsernames(String text) throws Exception {
        when(rates.rateToRub("USD")).thenReturn(new BigDecimal("90"));
        TelegramMessage message = entity(text, null);
        assertTrue(handler.needHandle(message), text);
        handler.handle(message);
        verify(rates).rateToRub("USD");
        verifyNoMoreInteractions(rates);
        verify(delivery).send(isNull(), argThat(answer -> answer.getText().contains("9")
                && answer.getText().contains("USD") && answer.getText().contains("RUB")));
    }

    private static TelegramMessage entity(String text, String caption) {
        Message message = new Message();
        Chat chat = mock(Chat.class);
        when(chat.getId()).thenReturn(123L);
        message.setChat(chat);
        message.setText(text);
        message.setCaption(caption);
        return new TelegramMessage(message, null);
    }
}
