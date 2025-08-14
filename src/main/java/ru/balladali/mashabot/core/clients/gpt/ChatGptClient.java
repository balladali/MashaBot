package ru.balladali.mashabot.core.clients.gpt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ChatGptClient {

    public record ChatMessage(String role, String content) {}

    public record ChatRequest(
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Integer max_tokens,
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
    private final Duration timeout;

    public ChatGptClient(OkHttpClient httpClient, String baseUrl, String apiKey, String model, Duration timeout) {
        this.http = httpClient.newBuilder().callTimeout(timeout).build();
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        HttpUrl u = HttpUrl.parse(baseUrl);
        if (u == null) throw new IllegalArgumentException("Bad baseUrl: " + baseUrl);
        this.baseUrl = u;
    }

    public String chat(List<ChatMessage> messages, Double temperature, Integer maxTokens) throws Exception {
        ChatRequest payload = new ChatRequest(model, messages, temperature, maxTokens, null);
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
