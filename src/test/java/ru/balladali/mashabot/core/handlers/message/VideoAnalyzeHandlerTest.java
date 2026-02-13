package ru.balladali.mashabot.core.handlers.message;

import org.junit.jupiter.api.Test;

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
        assertTrue(VideoAnalyzeHandler.hasAnalyzeTrigger("о чем видео?"));
        assertFalse(VideoAnalyzeHandler.hasAnalyzeTrigger("вот ссылка без запроса"));
    }
}
