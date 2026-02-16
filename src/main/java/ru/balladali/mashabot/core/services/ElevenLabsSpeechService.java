package ru.balladali.mashabot.core.services;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ElevenLabsSpeechService implements SpeechService {

    @Value("${credential.elevenlabs.api-key:}")
    private String apiKey;

    @Value("${credential.elevenlabs.voice-id:}")
    private String voiceId;

    @Value("${credential.elevenlabs.model-id:eleven_multilingual_v2}")
    private String modelId;

    @Override
    public InputStream synthesize(String text) throws IOException {
        if (apiKey == null || apiKey.isBlank() || voiceId == null || voiceId.isBlank() || text == null || text.isBlank()) {
            return null;
        }

        JSONObject body = new JSONObject();
        body.put("text", text);
        body.put("model_id", modelId);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.elevenlabs.io/v1/text-to-speech/" + voiceId))
                .timeout(Duration.ofSeconds(60))
                .header("xi-api-key", apiKey)
                .header("Accept", "audio/mpeg")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try {
            HttpResponse<byte[]> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return null;
            }
            byte[] audio = resp.body();
            if (audio == null || audio.length == 0) {
                return null;
            }
            return new ByteArrayInputStream(audio);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
