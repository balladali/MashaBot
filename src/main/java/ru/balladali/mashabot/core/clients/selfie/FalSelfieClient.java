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

    public FalSelfieClient(OkHttpClient httpClient, String endpoint, String apiKey, Duration timeout,
                           String promptBase, String promptWithUserScene, String promptWithRandomScene) {
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

        String payload = om.createObjectNode()
                .put("prompt", prompt)
                .put("image_url", dataUrl)
                .put("num_images", 1)
                .put("safety_tolerance", 2)
                .toString();

        Request req = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(payload, JSON))
                .build();

        String imageUrl;
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("FAL HTTP " + resp.code() + ": " + body);
            }
            imageUrl = extractImageUrl(body);
            if (imageUrl == null || imageUrl.isBlank()) {
                throw new IOException("FAL response does not contain image URL");
            }
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

    private String extractImageUrl(String body) throws Exception {
        JsonNode root = om.readTree(body);

        if (root.has("images") && root.get("images").isArray() && root.get("images").size() > 0) {
            JsonNode first = root.get("images").get(0);
            if (first.has("url")) return first.get("url").asText();
        }
        if (root.has("image") && root.get("image").has("url")) {
            return root.get("image").get("url").asText();
        }
        if (root.has("url")) return root.get("url").asText();
        if (root.has("output") && root.get("output").isArray() && root.get("output").size() > 0) {
            JsonNode first = root.get("output").get(0);
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
        String req = userRequest == null ? "" : userRequest.strip();
        boolean hasExplicitScene = hasExplicitSceneHint(req);
        String scene = scenes.get(new Random().nextInt(scenes.size()));

        String base = (promptBase == null || promptBase.isBlank())
                ? "Realistic smartphone selfie of the same woman as in the reference image. " +
                  "Photorealistic, natural skin texture, casual human vibe, candid shot as if taken right now. " +
                  "No stylization, no illustration, no CGI, no text overlay. " +
                  "Appearance, outfit and styling must match the scene, weather and season naturally. " +
                  "Avoid mismatches like summer clothes in winter scenes."
                : promptBase;

        if (hasExplicitScene) {
            String tpl = (promptWithUserScene == null || promptWithUserScene.isBlank())
                    ? "{base} Follow the user's requested scene/location as top priority. User request: {user_request}"
                    : promptWithUserScene;
            return tpl.replace("{base}", base)
                    .replace("{user_request}", req);
        }

        String tpl = (promptWithRandomScene == null || promptWithRandomScene.isBlank())
                ? "{base} Scene: {scene}. User request to follow if possible (without conflicting with scene realism): {user_request}"
                : promptWithRandomScene;
        return tpl.replace("{base}", base)
                .replace("{scene}", scene)
                .replace("{user_request}", req);
    }

    private static boolean hasExplicitSceneHint(String req) {
        if (req == null || req.isBlank()) return false;
        String r = req.toLowerCase();
        return r.matches(".*(на\\s+кухн|в\\s+кухн|на\\s+улиц|в\\s+парке|в\\s+офисе|дома|в\\s+комнате|в\\s+ванн|в\\s+машин|на\\s+балкон|в\\s+кафе|на\\s+пляж|в\\s+спортзале|в\\s+лесу|зим|лет|осен|весен|снег|дожд|ночью|утром|вечером).*");
    }
}
