package ru.balladali.mashabot.core.handlers.message;

import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
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
    private static final Pattern BOT_TRIGGER = Pattern.compile("^(?:маша[\\s,:-]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ANALYZE_TRIGGER = Pattern.compile("(проанализир(?:уй|овать|уйте)|анализ(?:ируй|ировать|)?)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final VideoAnalyzerClient client;

    public VideoAnalyzeHandler(VideoAnalyzerClient client) {
        this.client = client;
    }

    @Override
    public void handle(TelegramMessage entity) {
        String ytUrl = extractYoutubeUrlFromMessageOrReply(entity);
        if (ytUrl == null) return;

        String userPrompt = extractUserPrompt(entity);

        try {
            sendTyping(entity);
            sendAnswer(entity, "Секунду, разбираю видео по субтитрам…");

            VideoAnalyzerClient.AnalyzeResponse res = client.analyze(ytUrl, "ru,en", userPrompt);
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

    static boolean isAddressedToBot(String text) {
        return text != null && BOT_TRIGGER.matcher(text).find();
    }

    static String extractUserPrompt(TelegramMessage message) {
        String text = message != null ? message.getText() : null;
        if (text == null) return "проанализируй видео";

        String withoutTrigger = BOT_TRIGGER.matcher(text).replaceFirst("").trim();
        String withoutUrl = YT_URL.matcher(withoutTrigger).replaceAll("").trim();

        if (withoutUrl.isBlank()) {
            return "проанализируй видео";
        }
        return withoutUrl;
    }

    static String extractYoutubeUrlFromMessageOrReply(TelegramMessage message) {
        if (message == null) return null;

        String direct = extractYoutubeUrl(message.getText());
        if (direct != null) return direct;

        if (message.getMessage() != null && message.getMessage().getReplyToMessage() != null) {
            return extractYoutubeUrl(message.getMessage().getReplyToMessage().getText());
        }

        return null;
    }

    @Override
    public boolean needHandle(TelegramMessage message) {
        if (message == null || message.getText() == null) return false;
        String text = message.getText();
        return isAddressedToBot(text) && extractYoutubeUrlFromMessageOrReply(message) != null;
    }

    private String formatResult(VideoAnalyzerClient.AnalyzeResponse res) {
        if (res == null) return "Сервис вернул пустой ответ.";

        String status = Optional.ofNullable(res.status()).orElse("unknown");
        String answer = Optional.ofNullable(res.answer()).orElse("").trim();

        if (!"ok".equalsIgnoreCase(status)) {
            if (!answer.isBlank()) {
                return "Статус: " + status + "\n" + answer;
            }
            return "Статус: " + status;
        }

        if (!answer.isBlank()) {
            return answer;
        }

        return "Анализ завершён, но ответ пустой.";
    }

    @Override
    public void sendAnswer(TelegramMessage messageEntity, String answer) {
        String t = (answer == null) ? "" : answer.strip();
        if (t.isEmpty()) return;

        String md = toTelegramMarkdownV2(t);
        for (String part : splitForTelegram(md, TG_LIMIT)) {
            SendMessage msg = new SendMessage(messageEntity.getChatId(), part);
            msg.setParseMode(ParseMode.MARKDOWNV2);
            try {
                messageEntity.getClient().execute(msg);
            } catch (TelegramApiException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private static String toTelegramMarkdownV2(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String l = line.replace("```", "").trim();
            l = l.replaceFirst("^#{1,6}\\s+", "");
            l = l.replaceFirst("^\\d+[\\).]\\s+", "• ");
            l = l.replaceFirst("^[-*]\\s+", "• ");

            boolean headerLike = !l.startsWith("• ") && l.endsWith(":") && l.length() <= 80;
            String escaped = escapeMarkdownV2(l);
            if (headerLike && !escaped.isBlank()) {
                escaped = "*" + escaped + "*";
            }

            out.append(escaped);
            if (i < lines.length - 1) out.append('\n');
        }

        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private static String escapeMarkdownV2(String s) {
        if (s == null || s.isEmpty()) return "";
        String x = s.replace("\\", "\\\\");
        return x.replaceAll("([_\\*\\[\\]\\(\\)~`>#+\\-=|\\{\\}\\.!])", "\\\\$1");
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
