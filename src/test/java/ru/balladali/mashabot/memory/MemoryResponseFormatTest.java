package ru.balladali.mashabot.memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemoryResponseFormatTest {
    private final String header = "[2026-11-17T19:09:38Z] Маша (message=20202, reply=20201, bot=true):";
    @Test void removesReportedHeaderAndBackslash() {
        assertEquals("Привет!", MemoryResponseFormat.answerText(header + "\\\nПривет!"));
        assertEquals("Привет!", MemoryResponseFormat.answerText(header + "\nПривет!"));
        assertEquals("Привет!", MemoryResponseFormat.answerText(header + "\\nПривет!"));
    }
    @Test void keepsOrdinaryDatesAndQuotedHistory() {
        assertEquals("[2026-11-17] встречаемся", MemoryResponseFormat.answerText("[2026-11-17] встречаемся"));
        assertEquals("Цитата: " + header, MemoryResponseFormat.answerText("Цитата: " + header));
        assertEquals("Привет!", MemoryResponseFormat.answerText("Привет!"));
        assertNull(MemoryResponseFormat.answerText(null));
    }
}
