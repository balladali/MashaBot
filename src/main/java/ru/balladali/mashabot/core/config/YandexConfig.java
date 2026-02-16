package ru.balladali.mashabot.core.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.balladali.mashabot.core.services.ElevenLabsSpeechService;
import ru.balladali.mashabot.core.services.SpeechService;
import ru.balladali.mashabot.core.services.VoiceReplyService;
import ru.balladali.mashabot.core.services.YandexSpeechService;

@Configuration
public class YandexConfig {

    @Bean("yandexSpeechService")
    public YandexSpeechService yandexSpeechService() {
        return new YandexSpeechService();
    }

    @Bean("elevenLabsSpeechService")
    public ElevenLabsSpeechService elevenLabsSpeechService() {
        return new ElevenLabsSpeechService();
    }

    @Bean("speechService")
    public SpeechService speechService(
            @Qualifier("yandexSpeechService") YandexSpeechService yandex,
            @Qualifier("elevenLabsSpeechService") ElevenLabsSpeechService elevenLabs,
            @Value("${voice.provider:elevenlabs}") String provider
    ) {
        if (provider != null && provider.equalsIgnoreCase("yandex")) {
            return yandex;
        }
        return elevenLabs;
    }

    @Bean
    public VoiceReplyService voiceReplyService(@Qualifier("speechService") SpeechService speechService) {
        return new VoiceReplyService(speechService);
    }
}
