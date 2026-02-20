package ru.balladali.mashabot.core.services;

import org.springframework.beans.factory.annotation.Value;
import ru.balladali.mashabot.core.clients.selfie.FalSelfieClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SelfieService {

    private final FalSelfieClient client;

    @Value("${selfie.reference-path:}")
    private String referencePath;

    @Value("${selfie.daily-limit:5}")
    private int dailyLimit;

    @Value("${selfie.unlimited-user-ids:}")
    private String unlimitedUserIdsRaw;

    private LocalDate quotaDay = LocalDate.now(ZoneOffset.UTC);
    private final Map<Long, Integer> usedTodayByUser = new HashMap<>();

    public SelfieService(FalSelfieClient client) {
        this.client = client;
    }

    public synchronized boolean hasReference() {
        if (referencePath == null || referencePath.isBlank()) return false;
        Path p = Path.of(referencePath);
        return Files.exists(p) && Files.isRegularFile(p);
    }

    public synchronized boolean canGenerateNow(long userId) {
        rotateQuotaIfNeeded();
        if (isUnlimitedUser(userId)) return true;
        return usedTodayByUser.getOrDefault(userId, 0) < Math.max(0, dailyLimit);
    }

    public synchronized byte[] generate(long userId, String userRequest) throws Exception {
        rotateQuotaIfNeeded();
        if (!canGenerateNow(userId)) throw new IllegalStateException("daily_limit_exceeded");
        if (!hasReference()) throw new IllegalStateException("reference_not_found");

        byte[] ref = Files.readAllBytes(Path.of(referencePath));
        byte[] image = client.generateSelfie(ref, userRequest);
        if (!isUnlimitedUser(userId)) {
            usedTodayByUser.put(userId, usedTodayByUser.getOrDefault(userId, 0) + 1);
        }
        return image;
    }

    private void rotateQuotaIfNeeded() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        if (!now.equals(quotaDay)) {
            quotaDay = now;
            usedTodayByUser.clear();
        }
    }

    private boolean isUnlimitedUser(long userId) {
        if (userId == 0L || unlimitedUserIdsRaw == null || unlimitedUserIdsRaw.isBlank()) return false;
        Set<Long> ids = new HashSet<>();
        for (String part : unlimitedUserIdsRaw.split(",")) {
            String p = part.trim();
            if (p.isBlank()) continue;
            try {
                ids.add(Long.parseLong(p));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids.contains(userId);
    }
}
