package ru.balladali.mashabot.core.services;

import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.balladali.mashabot.telegram.TelegramMessage;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class VoiceReplyService {

    public record VoicePlan(boolean sendVoice, String voiceText, boolean alsoSendText) {}

    private final SpeechService speechService;

    @Value("${bot.voice-mode:false}")
    private boolean voiceModeEnabled;

    @Value("${voice.max-chars:450}")
    private int maxChars;

    @Value("${voice.daily-limit:20}")
    private int dailyLimit;

    @Value("${voice.min-interval-sec:20}")
    private int minIntervalSec;

    @Value("${voice.summary-on-long:true}")
    private boolean summaryOnLong;

    private LocalDate quotaDay = LocalDate.now(ZoneOffset.UTC);
    private int usedToday = 0;
    private long lastSentEpochSec = 0;

    public VoiceReplyService(SpeechService speechService) {
        this.speechService = speechService;
    }

    public synchronized VoicePlan buildPlan(String text) {
        String t = text == null ? "" : text.strip();
        if (!voiceModeEnabled || t.isEmpty() || maxChars <= 0) {
            return new VoicePlan(false, "", false);
        }

        rotateQuotaIfNeeded();
        long now = Instant.now().getEpochSecond();
        if (usedToday >= dailyLimit || (lastSentEpochSec > 0 && now - lastSentEpochSec < minIntervalSec)) {
            return new VoicePlan(false, "", false);
        }

        if (t.length() <= maxChars) {
            return new VoicePlan(true, t, false);
        }

        if (!summaryOnLong) {
            return new VoicePlan(false, "", false);
        }

        String shortText = summarizeForVoice(t, maxChars);
        if (shortText.isBlank()) {
            return new VoicePlan(false, "", false);
        }
        return new VoicePlan(true, shortText, true);
    }

    public synchronized boolean sendVoice(TelegramMessage messageEntity, String text) {
        if (messageEntity == null || text == null || text.isBlank()) return false;

        try (InputStream is = speechService.synthesize(text)) {
            if (is == null) return false;

            InputFile voiceFile = new InputFile(is, "answer.mp3");
            SendVoice sendVoice = new SendVoice(messageEntity.getChatId(), voiceFile);
            messageEntity.getClient().execute(sendVoice);

            rotateQuotaIfNeeded();
            usedToday += 1;
            lastSentEpochSec = Instant.now().getEpochSecond();
            return true;
        } catch (TelegramApiException | RuntimeException | java.io.IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void rotateQuotaIfNeeded() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        if (!now.equals(quotaDay)) {
            quotaDay = now;
            usedToday = 0;
        }
    }

    private static String summarizeForVoice(String text, int maxChars) {
        String t = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (t.length() <= maxChars) return t;

        int sentenceEnd = Math.max(t.lastIndexOf('.', maxChars), Math.max(t.lastIndexOf('!', maxChars), t.lastIndexOf('?', maxChars)));
        if (sentenceEnd > 80) {
            return t.substring(0, sentenceEnd + 1).strip();
        }

        int lineEnd = t.lastIndexOf('\n', maxChars);
        if (lineEnd > 80) {
            return t.substring(0, lineEnd).strip();
        }

        return t.substring(0, Math.max(1, maxChars - 1)).strip() + "…";
    }
}
