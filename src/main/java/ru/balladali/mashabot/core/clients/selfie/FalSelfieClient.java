package ru.balladali.mashabot.core.clients.selfie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Random;

public class FalSelfieClient {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient http;
    private final ObjectMapper om;
    private final String endpoint;
    private final String apiKey;
    private final String promptBase;
    private final String promptWithUserScene;
    private final String promptWithRandomScene;

    public FalSelfieClient(OkHttpClient httpClient, String endpoint, String apiKey, Duration timeout) {
        this(httpClient, endpoint, apiKey, timeout, null, null, null);
    }

    public FalSelfieClient(OkHttpClient httpClient,
                           String endpoint,
                           String apiKey,
                           Duration timeout,
                           String promptBase,
                           String promptWithUserScene,
                           String promptWithRandomScene) {
        this.http = httpClient.newBuilder().callTimeout(timeout).build();
        this.om = new ObjectMapper();
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.promptBase = promptBase;
        this.promptWithUserScene = promptWithUserScene;
        this.promptWithRandomScene = promptWithRandomScene;
    }

    public byte[] generateSelfie(byte[] referenceImage, String userRequest) throws Exception {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("FAL API key is not configured");
        if (endpoint == null || endpoint.isBlank()) throw new IllegalStateException("FAL endpoint is not configured");
        if (referenceImage == null || referenceImage.length == 0) throw new IllegalArgumentException("Reference image is empty");

        String prompt = buildPrompt(userRequest);
        String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(referenceImage);

        String submitPayload = om.createObjectNode()
                .set("input", om.createObjectNode()
                        .put("prompt", prompt)
                        .put("image_url", dataUrl)
                        .put("num_images", 1)
                        .put("output_format", "jpeg")
                )
                .toString();

        String imageUrl = submitAndAwaitImageUrl(submitPayload);

        Request imageReq = new Request.Builder().url(imageUrl).get().build();
        try (Response imgResp = http.newCall(imageReq).execute()) {
            if (!imgResp.isSuccessful() || imgResp.body() == null) {
                throw new IOException("Image download failed: HTTP " + imgResp.code());
            }
            byte[] bytes = imgResp.body().bytes();
            if (bytes.length == 0) throw new IOException("Image bytes are empty");
            return bytes;
        }
    }

    private String submitAndAwaitImageUrl(String submitPayload) throws Exception {
        String endpointId = extractEndpointId(endpoint);
        String submitUrl = "https://queue.fal.run/" + endpointId;

        Request submitReq = new Request.Builder()
                .url(submitUrl)
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(submitPayload, JSON))
                .build();

        String requestId;
        try (Response resp = http.newCall(submitReq).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("FAL submit HTTP " + resp.code() + ": " + body);
            }

            // Иногда endpoint может вернуть сразу готовую картинку
            String immediateUrl = extractImageUrl(body);
            if (immediateUrl != null && !immediateUrl.isBlank()) {
                return immediateUrl;
            }

            JsonNode root = om.readTree(body);
            JsonNode reqId = root.get("request_id");
            if (reqId == null || reqId.asText().isBlank()) {
                throw new IOException("FAL submit response does not contain request_id: " + body);
            }
            requestId = reqId.asText();
        }

        // Poll until completed
        String statusUrl = "https://queue.fal.run/" + endpointId + "/requests/" + requestId + "/status";
        String resultUrl = "https://queue.fal.run/" + endpointId + "/requests/" + requestId;

        int maxAttempts = 45; // ~90s total
        for (int i = 0; i < maxAttempts; i++) {
            String body = doGet(statusUrl);
            String imageUrl = extractImageUrl(body);
            if (imageUrl != null && !imageUrl.isBlank()) {
                return imageUrl;
            }

            JsonNode root = om.readTree(body);
            String status = root.path("status").asText("").toLowerCase();
            if ("completed".equals(status)) {
                String resultBody = doGet(resultUrl);
                String resultImage = extractImageUrl(resultBody);
                if (resultImage != null && !resultImage.isBlank()) {
                    return resultImage;
                }
                throw new IOException("FAL completed but no image URL in result");
            }
            if ("failed".equals(status) || "error".equals(status)) {
                throw new IOException("FAL job failed: " + body);
            }

            Thread.sleep(2000L);
        }

        throw new IOException("FAL job timeout waiting for result");
    }

    private String doGet(String url) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Key " + apiKey)
                .get()
                .build();

        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("FAL GET HTTP " + resp.code() + " (" + url + "): " + body);
            }
            return body;
        }
    }

    private String extractEndpointId(String endpointUrl) {
        String cleaned = endpointUrl.replaceFirst("^https?://[^/]+/", "");
        if (cleaned.startsWith("fal.run/")) {
            cleaned = cleaned.substring("fal.run/".length());
        }
        if (cleaned.startsWith("queue.fal.run/")) {
            cleaned = cleaned.substring("queue.fal.run/".length());
        }
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        while (cleaned.endsWith("/")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned;
    }

    private String extractImageUrl(String body) throws Exception {
        JsonNode root = om.readTree(body);

        // Common output shapes from fal queue/result
        JsonNode data = root.path("data");
        String fromData = extractImageUrlFromNode(data);
        if (fromData != null) return fromData;

        String fromRoot = extractImageUrlFromNode(root);
        if (fromRoot != null) return fromRoot;

        return null;
    }

    private String extractImageUrlFromNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;

        if (node.has("images") && node.get("images").isArray() && node.get("images").size() > 0) {
            JsonNode first = node.get("images").get(0);
            if (first.has("url")) return first.get("url").asText();
        }
        if (node.has("image") && node.get("image").has("url")) {
            return node.get("image").get("url").asText();
        }
        if (node.has("url") && node.get("url").isTextual()) {
            return node.get("url").asText();
        }
        if (node.has("output") && node.get("output").isArray() && node.get("output").size() > 0) {
            JsonNode first = node.get("output").get(0);
            if (first.has("url")) return first.get("url").asText();
            if (first.isTextual()) return first.asText();
        }
        return null;
    }

    private String buildPrompt(String userRequest) {
        List<String> scenes = List.of(
                "in a cozy apartment near a window with soft daylight",
                "on a city street in natural daylight with candid vibe",
                "in a cafe, casual candid selfie with realistic lighting",
                "at home mirror selfie with natural imperfections",
                "outdoor park selfie, handheld phone, realistic skin texture"
        );
        String scene = scenes.get(new Random().nextInt(scenes.size()));
        String req = userRequest == null ? "" : userRequest.strip();

        String base = (promptBase == null || promptBase.isBlank())
                ? "Realistic smartphone selfie of the same woman as in the reference image. " +
                  "Photorealistic, natural skin texture, casual human vibe, candid shot as if taken right now. " +
                  "No stylization, no illustration, no CGI, no text overlay."
                : promptBase;

        if (!req.isBlank()) {
            if (promptWithUserScene != null && !promptWithUserScene.isBlank()) {
                return promptWithUserScene
                        .replace("{base}", base)
                        .replace("{request}", req)
                        .replace("{scene}", scene);
            }
            return base + " Scene: " + scene + ". User request to follow if possible: " + req;
        }

        if (promptWithRandomScene != null && !promptWithRandomScene.isBlank()) {
            return promptWithRandomScene
                    .replace("{base}", base)
                    .replace("{scene}", scene);
        }
        return base + " Scene: " + scene + ".";
    }
}
