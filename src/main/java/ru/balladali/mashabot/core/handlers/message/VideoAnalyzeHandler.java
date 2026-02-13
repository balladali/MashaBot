package ru.balladali.mashabot.core.handlers.message;

import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.balladali.mashabot.core.clients.video.VideoAnalyzerClient;
import ru.balladali.mashabot.telegram.TelegramMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoAnalyzeHandler implements MessageHandler {
    private static final int TG_LIMIT = 4096;
    private static final Pattern YT_URL = Pattern.compile("(https?://(?:www\\.)?(?:youtube\\.com/(?:watch\\?v=[^\\s&]+[^\\s]*|shorts/[^\\s?]+[^\\s]*)|youtu\\.be/[^\\s?]+[^\\s]*))", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANALYZE_TRIGGER = Pattern.compile("(проанализир(?:уй|овать|уйте)|анализ(?:ируй|ировать|)|о\\s*ч[её]м\\s*видео)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final VideoAnalyzerClient client;

    public VideoAnalyzeHandler(VideoAnalyzerClient client) {
        this.client = client;
    }

    @Override
    public void handle(TelegramMessage entity) {
        String text = Optional.ofNullable(entity.getText()).orElse("");
        String ytUrl = extractYoutubeUrl(text);
        if (ytUrl == null) return;

        try {
            sendTyping(entity);
            sendAnswer(entity, "Секунду, разбираю видео по субтитрам…");

            VideoAnalyzerClient.AnalyzeResponse res = client.analyze(ytUrl, "ru,en");
            String answer = formatResult(res);
            sendAnswer(entity, answer);
        } catch (Exception e) {
            e.printStackTrace();
            sendAnswer(entity, "Не удалось проанализировать видео. Попробуй ещё раз чуть позже 🙏");
        }
    }

    static String extractYoutubeUrl(String text) {
        if (text == null) return null;
        Matcher m = YT_URL.matcher(text);
        if (m.find()) return m.group(1);
        return null;
    }

    static boolean hasAnalyzeTrigger(String text) {
        return text != null && ANALYZE_TRIGGER.matcher(text).find();
    }

    @Override
    public boolean needHandle(TelegramMessage message) {
        if (message == null || message.getText() == null) return false;
        String text = message.getText();
        return extractYoutubeUrl(text) != null && hasAnalyzeTrigger(text);
    }

    private String formatResult(VideoAnalyzerClient.AnalyzeResponse res) {
        if (res == null) return "Сервис вернул пустой ответ.";

        StringBuilder sb = new StringBuilder();
        String status = Optional.ofNullable(res.status()).orElse("unknown");

        if (!"ok".equalsIgnoreCase(status)) {
            sb.append("Статус: ").append(status).append("\n");
            if (res.summary() != null && !res.summary().isBlank()) {
                sb.append(res.summary());
            }
            return sb.toString().trim();
        }

        sb.append("Коротко по видео:\n");
        if (res.summary() != null && !res.summary().isBlank()) {
            sb.append(res.summary()).append("\n\n");
        }

        List<String> points = res.key_points();
        if (points != null && !points.isEmpty()) {
            sb.append("Ключевые пункты:\n");
            for (int i = 0; i < points.size(); i++) {
                sb.append(i + 1).append(") ").append(points.get(i)).append("\n");
            }
        }

        return sb.toString().trim();
    }

    @Override
    public void sendAnswer(TelegramMessage messageEntity, String answer) {
        String t = (answer == null) ? "" : answer.strip();
        if (t.isEmpty()) return;

        for (String part : splitForTelegram(t, TG_LIMIT)) {
            SendMessage msg = new SendMessage(messageEntity.getChatId(), part);
            try {
                messageEntity.getClient().execute(msg);
            } catch (TelegramApiException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private static List<String> splitForTelegram(String s, int limit) {
        List<String> parts = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String line : s.split("\\n", -1)) {
            if (buf.length() + line.length() + 1 > limit) {
                if (!buf.isEmpty()) {
                    parts.add(buf.toString());
                    buf.setLength(0);
                }
                while (line.length() > limit) {
                    parts.add(line.substring(0, limit));
                    line = line.substring(limit);
                }
            }
            if (!buf.isEmpty()) buf.append('\n');
            buf.append(line);
        }
        if (!buf.isEmpty()) parts.add(buf.toString());
        return parts;
    }

    private void sendTyping(TelegramMessage messageEntity) {
        SendChatAction action = new SendChatAction(messageEntity.getChatId(), ActionType.TYPING.toString());
        try {
            messageEntity.getClient().execute(action);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
