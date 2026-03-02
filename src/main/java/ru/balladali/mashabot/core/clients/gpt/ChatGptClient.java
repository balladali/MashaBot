package ru.balladali.mashabot.core.clients.gpt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ChatGptClient {

    public record ChatMessage(String role, String content) {}

    public record ChatRequest(
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Integer max_tokens,
            Boolean stream,
            Map<String, Object> response_format // можно null
    ) {}

    public record Choice( int index, Message message ) {
        public record Message(String role, String content) {}
    }

    public record ChatResponse( List<Choice> choices ) {}

    private final OkHttpClient http;
    private final ObjectMapper om;
    private final HttpUrl baseUrl;
    private final String apiKey;
    private final String model;

    public ChatGptClient(OkHttpClient httpClient, String baseUrl, String apiKey, String model, Duration timeout) {
        this.http = httpClient.newBuilder().callTimeout(timeout).build();
        this.apiKey = apiKey;
        this.model = model;
        this.om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        HttpUrl u = HttpUrl.parse(baseUrl);
        if (u == null) throw new IllegalArgumentException("Bad baseUrl: " + baseUrl);
        this.baseUrl = u;
    }

    public String chat(List<ChatMessage> messages, Double temperature, Integer maxTokens) throws Exception {
        ChatRequest payload = new ChatRequest(model, messages, temperature, maxTokens, false, null);
        return executeNonStream(payload);
    }

    public String chatStream(List<ChatMessage> messages, Double temperature, Integer maxTokens, Consumer<String> onDelta) throws Exception {
        ChatRequest payload = new ChatRequest(model, messages, temperature, maxTokens, true, null);
        String json = om.writeValueAsString(payload);

        Request req = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegment("v1").addPathSegment("chat").addPathSegment("completions").build())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IllegalStateException("Chat API HTTP " + resp.code() + ": " + body);
            }

            if (resp.body() == null) {
                throw new IllegalStateException("Empty streaming response body");
            }

            StringBuilder full = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body().byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    JsonNode root = om.readTree(data);
                    JsonNode choices = root.path("choices");
                    if (!choices.isArray() || choices.isEmpty()) {
                        continue;
                    }

                    JsonNode delta = choices.get(0).path("delta").path("content");
                    if (delta.isTextual()) {
                        String token = delta.asText();
                        if (!token.isEmpty()) {
                            full.append(token);
                            onDelta.accept(token);
                        }
                    }
                }
            }
            return full.toString();
        }
    }

    private String executeNonStream(ChatRequest payload) throws Exception {
        String json = om.writeValueAsString(payload);

        Request req = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegment("v1").addPathSegment("chat").addPathSegment("completions").build())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IllegalStateException("Chat API HTTP " + resp.code() + ": " + body);
            }
            String body = resp.body().string();
            ChatResponse cr = om.readValue(body, ChatResponse.class);
            if (cr.choices() == null || cr.choices().isEmpty() || cr.choices().get(0).message() == null) {
                throw new IllegalStateException("Empty completion response");
            }
            return cr.choices().get(0).message().content();
        }
    }
}
