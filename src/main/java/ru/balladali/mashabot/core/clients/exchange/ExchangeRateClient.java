package ru.balladali.mashabot.core.clients.exchange;

import java.math.BigDecimal;

public interface ExchangeRateClient {
    /**
     * @param iso ISO 4217, например "USD"
     * @return курс RUB за 1 единицу валюты (RUB per 1 ISO). Например: USD -> 89.45
     */
    BigDecimal rateToRub(String iso) throws Exception;
}
