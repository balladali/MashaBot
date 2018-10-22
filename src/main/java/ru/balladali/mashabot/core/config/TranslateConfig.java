package ru.balladali.mashabot.core.config;

import co.aurasphere.jyandex.Jyandex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TranslateConfig {
    @Value("${credential.yandex.translate.key}")
    private String YANDEX_API_KEY;

    @Bean("yandexTranslator")
    public Jyandex getYandexTranslator() {
        return new Jyandex(YANDEX_API_KEY);
    }
}
