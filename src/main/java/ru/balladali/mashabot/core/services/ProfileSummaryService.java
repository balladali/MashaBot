package ru.balladali.mashabot.core.services;

import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ProfileSummaryService {
    private static final String SUMMARY_MARKER = "## Summary @ ";
    private static final int PROFILE_COMPACT_THRESHOLD = 10;
    private static final int PROFILE_KEEP_RECENT_SUMMARIES = 2;

    private final ChatGptClient chat;
    private final Path profileDir;

    public ProfileSummaryService(ChatGptClient chat, String profileDir) {
        this.chat = chat;
        String dir = (profileDir == null || profileDir.isBlank()) ? "/opt/masha/memory" : profileDir;
        this.profileDir = Paths.get(dir);
    }

    public String loadForPrompt(String key, int maxChars) {
        Path file = profileFilePath(key);
        if (!Files.exists(file)) return "";
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.length() <= maxChars) return content;
            return content.substring(content.length() - maxChars);
        } catch (IOException e) {
            return "";
        }
    }

    public String generateDialogSummary(String transcript) throws Exception {
        List<ChatGptClient.ChatMessage> summaryPrompt = new ArrayList<>();
        summaryPrompt.add(new ChatGptClient.ChatMessage("system", "Сделай краткую долговременную выжимку диалога. Выдели только стабильные факты о пользователе, договоренности и долгоживущий контекст. Без воды, 15-20 пунктов."));
        summaryPrompt.add(new ChatGptClient.ChatMessage("user", transcript));
        return chat.chat(summaryPrompt, 0.2, 350);
    }

    public synchronized void appendSummaryAndMaybeResummarize(String key, String summary) throws Exception {
        if (summary == null || summary.isBlank()) return;

        Path file = profileFilePath(key);
        Files.createDirectories(file.getParent());

        String existing = Files.exists(file)
                ? Files.readString(file, StandardCharsets.UTF_8)
                : "# User Long-Term Memory\n\n";

        String section = SUMMARY_MARKER + Instant.now() + "\n" + summary.strip() + "\n\n";
        writeAtomically(file, existing + section);

        maybeResummarizeProfile(file);
    }

    private void maybeResummarizeProfile(Path file) throws Exception {
        if (!Files.exists(file)) return;

        String content = Files.readString(file, StandardCharsets.UTF_8);
        List<String> sections = extractSummarySections(content);
        if (sections.size() < PROFILE_COMPACT_THRESHOLD) return;

        int keepRecent = Math.min(PROFILE_KEEP_RECENT_SUMMARIES, sections.size());
        int compactCount = sections.size() - keepRecent;
        if (compactCount <= 1) return;

        StringBuilder oldSummaries = new StringBuilder();
        for (int i = 0; i < compactCount; i++) {
            oldSummaries.append(sections.get(i)).append("\n\n");
        }

        List<ChatGptClient.ChatMessage> prompt = new ArrayList<>();
        prompt.add(new ChatGptClient.ChatMessage("system", "Сделай ресаммари уже существующих Summary-блоков в один новый Summary. Сохрани только стабильные факты о пользователе, договоренности и долгоживущий контекст. 15-20 пунктов, без воды и дублей."));
        prompt.add(new ChatGptClient.ChatMessage("user", oldSummaries.toString()));

        String merged = chat.chat(prompt, 0.2, 450);

        StringBuilder rebuilt = new StringBuilder("# User Long-Term Memory\n\n");
        rebuilt.append(SUMMARY_MARKER).append(Instant.now()).append("\n").append(merged.strip()).append("\n\n");
        for (int i = compactCount; i < sections.size(); i++) {
            rebuilt.append(sections.get(i)).append("\n\n");
        }

        writeAtomically(file, rebuilt.toString());
    }

    private List<String> extractSummarySections(String content) {
        List<String> sections = new ArrayList<>();
        int idx = content.indexOf(SUMMARY_MARKER);
        while (idx >= 0) {
            int next = content.indexOf(SUMMARY_MARKER, idx + SUMMARY_MARKER.length());
            String section = (next >= 0) ? content.substring(idx, next) : content.substring(idx);
            section = section.strip();
            if (!section.isBlank()) {
                sections.add(section);
            }
            idx = next;
        }
        return sections;
    }

    private void writeAtomically(Path file, String content) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Path profileFilePath(String key) {
        String[] parts = key.split(":", 2);
        String chatId = sanitize(parts.length > 0 ? parts[0] : "unknown_chat");
        String userId = sanitize(parts.length > 1 ? parts[1] : "unknown_user");
        return profileDir.resolve(chatId).resolve(userId + ".md");
    }

    private String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
