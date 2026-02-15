package ru.balladali.mashabot.core.clients.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoAnalyzerClientJsonTest {

    @Test
    void analyzeResponseRecord_shouldExposeAnswerAccessor() {
        VideoAnalyzerClient.AnalyzeResponse r = new VideoAnalyzerClient.AnalyzeResponse(
                "u", "ok", "a", "t"
        );
        assertEquals("a", r.answer());
    }
}
