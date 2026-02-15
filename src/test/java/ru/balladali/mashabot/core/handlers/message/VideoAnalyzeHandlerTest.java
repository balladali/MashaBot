package ru.balladali.mashabot.core.handlers.message;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.balladali.mashabot.telegram.TelegramMessage;

import static org.junit.jupiter.api.Assertions.*;

class VideoAnalyzeHandlerTest {

    @Test
    void extractYoutubeUrl_supportsWatchShortsAndShortLink() {
        String watch = "посмотри https://www.youtube.com/watch?v=abcDEF12345";
        String shorts = "https://youtube.com/shorts/VVh_1g3mpj0?si=test";
        String shortUrl = "https://youtu.be/5uyCAExOoUk?si=abc";

        assertEquals("https://www.youtube.com/watch?v=abcDEF12345", VideoAnalyzeHandler.extractYoutubeUrl(watch));
        assertEquals("https://youtube.com/shorts/VVh_1g3mpj0?si=test", VideoAnalyzeHandler.extractYoutubeUrl(shorts));
        assertEquals("https://youtu.be/5uyCAExOoUk?si=abc", VideoAnalyzeHandler.extractYoutubeUrl(shortUrl));
    }

    @Test
    void extractYoutubeUrl_returnsNullForNonYoutube() {
        assertNull(VideoAnalyzeHandler.extractYoutubeUrl("https://example.com/video"));
        assertNull(VideoAnalyzeHandler.extractYoutubeUrl("просто текст"));
    }

    @Test
    void hasAnalyzeTrigger_detectsRussianAnalyzePhrases() {
        assertTrue(VideoAnalyzeHandler.hasAnalyzeTrigger("проанализируй это видео"));
        assertTrue(VideoAnalyzeHandler.hasAnalyzeTrigger("анализ https://youtu.be/abc"));
        assertFalse(VideoAnalyzeHandler.hasAnalyzeTrigger("вот ссылка без запроса"));
    }

    @Test
    void isAddressedToBot_detectsMashaPrefix() {
        assertTrue(VideoAnalyzeHandler.isAddressedToBot("Маша, расскажи 5 моментов"));
        assertTrue(VideoAnalyzeHandler.isAddressedToBot("маша проанализируй"));
        assertFalse(VideoAnalyzeHandler.isAddressedToBot("расскажи 5 моментов"));
    }

    @Test
    void extractYoutubeUrlFromMessageOrReply_usesReplyTextWhenNoLinkInMessage() {
        Message reply = new Message();
        reply.setText("https://youtu.be/5uyCAExOoUk?si=abc");

        Message msg = new Message();
        msg.setText("проанализируй");
        msg.setReplyToMessage(reply);

        TelegramMessage tm = new TelegramMessage(msg, null);
        String found = VideoAnalyzeHandler.extractYoutubeUrlFromMessageOrReply(tm);

        assertEquals("https://youtu.be/5uyCAExOoUk?si=abc", found);
    }

    @Test
    void extractYoutubeUrlFromMessageOrReply_prefersDirectMessageLink() {
        Message reply = new Message();
        reply.setText("https://youtu.be/replyLink");

        Message msg = new Message();
        msg.setText("Маша, проанализируй https://youtube.com/shorts/VVh_1g3mpj0?si=test");
        msg.setReplyToMessage(reply);

        TelegramMessage tm = new TelegramMessage(msg, null);
        String found = VideoAnalyzeHandler.extractYoutubeUrlFromMessageOrReply(tm);

        assertEquals("https://youtube.com/shorts/VVh_1g3mpj0?si=test", found);
    }

    @Test
    void extractUserPrompt_removesTriggerAndUrl() {
        Message msg = new Message();
        msg.setText("Маша, расскажи 5 основных моментов из этого видео https://youtu.be/5uyCAExOoUk");
        TelegramMessage tm = new TelegramMessage(msg, null);

        assertEquals("расскажи 5 основных моментов из этого видео", VideoAnalyzeHandler.extractUserPrompt(tm));
    }
}
