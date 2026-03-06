package ru.balladali.mashabot.core.clients.selfie;

import ai.fal.client.*;
import ai.fal.client.queue.QueueResultOptions;
import ai.fal.client.queue.QueueStatus;
import ai.fal.client.queue.QueueStatusOptions;
import ai.fal.client.queue.QueueSubmitOptions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Random;

public class FalSelfieClient {

    private final OkHttpClient http;
    private final String endpoint;
    private final String promptBase;
    private final String promptWithUserScene;
    private final String promptWithRandomScene;
    private final FalClient fal;

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
        this.endpoint = normalizeEndpointId(endpoint);
        this.promptBase = promptBase;
        this.promptWithUserScene = promptWithUserScene;
        this.promptWithRandomScene = promptWithRandomScene;

        this.fal = FalClient.withConfig(
                new ClientConfig.Builder()
                        .withCredentials(CredentialsResolver.fromApiKey(apiKey))
                        .build()
        );
    }

    public byte[] generateSelfie(byte[] referenceImage, String userRequest) throws Exception {
        if (endpoint == null || endpoint.isBlank()) throw new IllegalStateException("FAL endpoint is not configured");
        if (referenceImage == null || referenceImage.length == 0) throw new IllegalArgumentException("Reference image is empty");

        String prompt = buildPrompt(userRequest);
        String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(referenceImage);

        JsonObject input = JsonInput.input()
                .set("prompt", prompt)
                .set("image_url", dataUrl)
                .set("num_images", 1)
                .set("output_format", "jpeg")
                .build();

        QueueStatus.InQueue submitted = fal.queue().submit(endpoint, QueueSubmitOptions.withInput(input));
        String requestId = submitted.getRequestId();
        if (requestId == null || requestId.isBlank()) {
            throw new IOException("FAL did not return requestId");
        }

        waitForCompletion(requestId);

        Output<JsonObject> result = fal.queue().result(
                endpoint,
                QueueResultOptions.<JsonObject>builder()
                        .requestId(requestId)
                        .resultType(JsonObject.class)
                        .build()
        );

        JsonObject data = result.getData();
        String imageUrl = extractImageUrl(data);
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IOException("FAL response does not contain image URL");
        }

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

    private String normalizeEndpointId(String endpointValue) {
        if (endpointValue == null) return null;
        String cleaned = endpointValue.replaceFirst("^https?://[^/]+/", "");
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        while (cleaned.endsWith("/")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned;
    }

    private void waitForCompletion(String requestId) throws Exception {
        int maxAttempts = 45; // ~90s
        for (int i = 0; i < maxAttempts; i++) {
            QueueStatus.StatusUpdate status = fal.queue().status(
                    endpoint,
                    QueueStatusOptions.builder().requestId(requestId).logs(false).build()
            );

            QueueStatus.Status s = status.getStatus();
            if (QueueStatus.Status.COMPLETED.equals(s)) {
                return;
            }
            if (!(QueueStatus.Status.IN_QUEUE.equals(s) || QueueStatus.Status.IN_PROGRESS.equals(s))) {
                throw new IOException("FAL job failed with status: " + s);
            }

            Thread.sleep(2000L);
        }
        throw new IOException("FAL job timeout waiting for completion");
    }

    private String extractImageUrl(JsonObject node) {
        if (node == null) return null;

        if (node.has("images") && node.get("images").isJsonArray()) {
            JsonArray arr = node.getAsJsonArray("images");
            if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                JsonObject first = arr.get(0).getAsJsonObject();
                if (first.has("url")) return first.get("url").getAsString();
            }
        }
        if (node.has("image") && node.get("image").isJsonObject()) {
            JsonObject image = node.getAsJsonObject("image");
            if (image.has("url")) return image.get("url").getAsString();
        }
        if (node.has("url")) return node.get("url").getAsString();
        if (node.has("data") && node.get("data").isJsonObject()) {
            return extractImageUrl(node.getAsJsonObject("data"));
        }
        if (node.has("output") && node.get("output").isJsonArray()) {
            JsonArray arr = node.getAsJsonArray("output");
            if (!arr.isEmpty()) {
                if (arr.get(0).isJsonObject()) {
                    JsonObject first = arr.get(0).getAsJsonObject();
                    if (first.has("url")) return first.get("url").getAsString();
                }
                if (arr.get(0).isJsonPrimitive()) {
                    return arr.get(0).getAsString();
                }
            }
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
                        .replace("{user_request}", req)
                        .replace("{scene}", scene);
            }
            return base + " Scene: " + scene + ". User request to follow if possible: " + req;
        }

        if (promptWithRandomScene != null && !promptWithRandomScene.isBlank()) {
            return promptWithRandomScene
                    .replace("{base}", base)
                    .replace("{scene}", scene)
                    .replace("{request}", req)
                    .replace("{user_request}", req);
        }
        return base + " Scene: " + scene + ".";
    }
}
