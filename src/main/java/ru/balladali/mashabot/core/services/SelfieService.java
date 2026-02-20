package ru.balladali.mashabot.core.services;

import org.springframework.beans.factory.annotation.Value;
import ru.balladali.mashabot.core.clients.selfie.FalSelfieClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class SelfieService {

    private final FalSelfieClient client;

    @Value("${selfie.reference-path:}")
    private String referencePath;

    @Value("${selfie.daily-limit:5}")
    private int dailyLimit;

    private LocalDate quotaDay = LocalDate.now(ZoneOffset.UTC);
    private int usedToday = 0;

    public SelfieService(FalSelfieClient client) {
        this.client = client;
    }

    public synchronized boolean hasReference() {
        if (referencePath == null || referencePath.isBlank()) return false;
        Path p = Path.of(referencePath);
        return Files.exists(p) && Files.isRegularFile(p);
    }

    public synchronized boolean canGenerateNow() {
        rotateQuotaIfNeeded();
        return usedToday < Math.max(0, dailyLimit);
    }

    public synchronized byte[] generate(String userRequest) throws Exception {
        rotateQuotaIfNeeded();
        if (!canGenerateNow()) throw new IllegalStateException("daily_limit_exceeded");
        if (!hasReference()) throw new IllegalStateException("reference_not_found");

        byte[] ref = Files.readAllBytes(Path.of(referencePath));
        byte[] image = client.generateSelfie(ref, userRequest);
        usedToday += 1;
        return image;
    }

    private void rotateQuotaIfNeeded() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        if (!now.equals(quotaDay)) {
            quotaDay = now;
            usedToday = 0;
        }
    }
}
