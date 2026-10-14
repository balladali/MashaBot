package ru.balladali.mashabot.core.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.balladali.mashabot.core.clients.gpt.ChatGptClient;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProfileSummaryServiceTest {
    @TempDir Path dir;

    @Test
    void failedCompactionDoesNotDiscardOrReplayAnAlreadyPersistedSummary() throws Exception {
        ChatGptClient chat = mock(ChatGptClient.class);
        when(chat.chat(anyList(), anyDouble(), anyInt())).thenThrow(new IllegalStateException("upstream failed"));
        ProfileSummaryService profiles = new ProfileSummaryService(chat, dir.toString());
        for (int i = 0; i < 10; i++) profiles.appendSummaryAndMaybeResummarize("1:2", "batch-" + i);
        String content = Files.readString(dir.resolve("1/2.md"));
        assertEquals(10, content.split("## Summary @ ", -1).length - 1);
        assertTrue(content.contains("batch-9"));
        assertFalse(Files.exists(dir.resolve("1/2.md.tmp")));
        verify(chat).chat(anyList(), anyDouble(), anyInt());
    }
}
