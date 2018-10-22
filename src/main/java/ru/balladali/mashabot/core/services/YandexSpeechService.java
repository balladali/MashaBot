package ru.balladali.mashabot.core.services;

import org.springframework.beans.factory.annotation.Value;

import javax.ws.rs.core.UriBuilder;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;

public class YandexSpeechService implements SpeechService {

    private static final String ANSWER_URL = "https://tts.voicetech.yandex.net/generate";

    @Value("${credential.yandex.speech.key}")
    private String YANDEX_API_KEY;

    @Override
    public InputStream synthesize(String text) {
        String url = UriBuilder.fromUri(ANSWER_URL)
                .queryParam("key", YANDEX_API_KEY)
                .queryParam("text", text)
                .queryParam("format", "mp3")
                .queryParam("lang", "ru-RU")
                .queryParam("speaker", "alyss")
                .toTemplate();

        URLConnection connection;
        try {
            connection = new URL(url).openConnection();
            return connection.getInputStream();
        } catch (IOException e) {
            return null;
        }
    }
}
