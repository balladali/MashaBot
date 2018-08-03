package ru.balladali.balladalibot.balladalibot.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.balladali.balladalibot.balladalibot.core.services.YandexSpeechService;

@Configuration
public class YandexConfig {

    @Bean("yandexSpeechService")
    public YandexSpeechService yandexSpeechService() {
        return new YandexSpeechService();
    }
}
