package ru.balladali.mashabot.core.clients.video;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.time.Duration;
import java.util.List;

public class VideoAnalyzerClient {

    public record AnalyzeRequest(String url, String lang, String user_prompt) {}

    public record AnalyzeResponse(
            String url,
            String status,
            String answer,
            String summary,
            List<String> key_points,
            String transcript
    ) {}

    private final OkHttpClient http;
    private final ObjectMapper om;
    private final HttpUrl analyzeUrl;

    public VideoAnalyzerClient(OkHttpClient httpClient, String baseUrl, Duration timeout) {
        this.http = httpClient.newBuilder().callTimeout(timeout).build();
        this.om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        HttpUrl u = HttpUrl.parse(baseUrl);
        if (u == null) throw new IllegalArgumentException("Bad video analyzer URL: " + baseUrl);
        this.analyzeUrl = u;
    }

    public AnalyzeResponse analyze(String url, String lang, String userPrompt) throws Exception {
        AnalyzeRequest payload = new AnalyzeRequest(url, lang, userPrompt);
        String json = om.writeValueAsString(payload);

        Request req = new Request.Builder()
                .url(analyzeUrl)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IllegalStateException("Video analyzer HTTP " + resp.code() + ": " + body);
            }
            String body = resp.body() != null ? resp.body().string() : "{}";
            return om.readValue(body, AnalyzeResponse.class);
        }
    }
}
