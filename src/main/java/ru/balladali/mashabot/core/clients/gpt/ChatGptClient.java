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

    public record Reasoning(boolean enabled) {}

    public record ChatRequest(
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Integer max_tokens,
            Map<String, Object> response_format, // можно null
            Reasoning reasoning
    ) {}

    public record Choice(int index, Message message) {
        public record Message(String role, String content) {}
    }

    public record ChatResponse(List<Choice> choices) {}

    private final OkHttpClient http;
    private final ObjectMapper om;
    private final HttpUrl baseUrl;
    private final String apiKey;
    private final String model;
    private final boolean reasoningEnabled;

    public ChatGptClient(OkHttpClient httpClient, String baseUrl, String apiKey, String model, Duration timeout) {
        this(httpClient, baseUrl, apiKey, model, timeout, false);
    }

    public ChatGptClient(OkHttpClient httpClient, String baseUrl, String apiKey, String model, Duration timeout,
                         boolean reasoningEnabled) {
        this.http = httpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(timeout)
                .writeTimeout(Duration.ofSeconds(30))
                .callTimeout(timeout.plusSeconds(10))
                .retryOnConnectionFailure(true)
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningEnabled = reasoningEnabled;
        this.om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        HttpUrl u = HttpUrl.parse(baseUrl);
        if (u == null) throw new IllegalArgumentException("Bad baseUrl: " + baseUrl);
        this.baseUrl = u;
    }

    public String chat(List<ChatMessage> messages, Double temperature, Integer maxTokens) throws Exception {
        long started = System.nanoTime();
        ChatRequest payload = new ChatRequest(model, messages, temperature, maxTokens, null, new Reasoning(reasoningEnabled));
        String json = om.writeValueAsString(payload);

        Request req = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegment("v1").addPathSegment("chat").addPathSegment("completions").build())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            org.slf4j.LoggerFactory.getLogger(ChatGptClient.class).info("event=gpt_response code={} duration_ms={}",
                    resp.code(), (System.nanoTime() - started) / 1_000_000);
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("Chat API HTTP " + resp.code());
            }
            String body = resp.body().string();
            ChatResponse cr = om.readValue(body, ChatResponse.class);
            if (cr.choices() == null || cr.choices().isEmpty() || cr.choices().get(0).message() == null) {
                throw new IllegalStateException("Empty completion response");
            }
            String answer = cr.choices().get(0).message().content();
            if (answer == null || answer.isBlank()) throw new IllegalStateException("Empty completion response");
            return answer;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ChatGptClient.class).warn("event=gpt_failed type={}", e.getClass().getSimpleName());
            throw e;
        } finally {
            org.slf4j.LoggerFactory.getLogger(ChatGptClient.class).info("event=gpt_finished duration_ms={}",
                    (System.nanoTime() - started) / 1_000_000);
        }
    }
}
