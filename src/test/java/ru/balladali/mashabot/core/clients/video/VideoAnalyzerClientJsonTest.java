package ru.balladali.mashabot.core.clients.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoAnalyzerClientJsonTest {

    @Test
    void analyzeResponseRecord_shouldMapSnakeCaseKeyPointsAccessor() {
        // Компиляционный smoke-test: проверяем accessor name для key_points
        VideoAnalyzerClient.AnalyzeResponse r = new VideoAnalyzerClient.AnalyzeResponse(
                "u", "ok", "a", "s", java.util.List.of("a", "b"), "t"
        );
        assertTrue(r.key_points().size() == 2);
    }
}
