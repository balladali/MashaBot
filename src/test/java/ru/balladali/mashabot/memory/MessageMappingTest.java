package ru.balladali.mashabot.memory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.message.Message;

class MessageMappingTest {
    @Test
    void captionThreadReplyAndMissingAuthor() {
        var m = mock(Message.class);
        when(m.getChatId()).thenReturn(-1L);
        when(m.getMessageId()).thenReturn(42);
        when(m.getDate()).thenReturn(100);
        when(m.getEditDate()).thenReturn(200);
        when(m.getMessageThreadId()).thenReturn(7);
        when(m.hasPhoto()).thenReturn(true);
        when(m.getCaption()).thenReturn("caption");
        var parent = mock(Message.class);
        when(parent.getMessageId()).thenReturn(41);
        when(m.getReplyToMessage()).thenReturn(parent);
        var mapped = ChatMessage.from(m, false);
        assertEquals("PHOTO", mapped.type());
        assertEquals("caption", mapped.text());
        assertEquals(7L, mapped.threadId());
        assertEquals(41L, mapped.replyId());
        assertNull(mapped.userId());
        assertEquals(200, mapped.edited().getEpochSecond());
        assertEquals("OUTGOING", ChatMessage.from(m, true).direction());
        assertTrue(ChatMessage.from(m, true).bot());
    }
}
