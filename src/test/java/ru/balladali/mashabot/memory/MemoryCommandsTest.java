package ru.balladali.mashabot.memory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberAdministrator;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberMember;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import ru.balladali.mashabot.telegram.TelegramDelivery;

class MemoryCommandsTest {
    @Test
    void groupAuthorizationAndChatScope() throws Exception {
        var memory = mock(ChatMemoryService.class);
        var delivery = mock(TelegramDelivery.class);
        var client = mock(TelegramClient.class);
        var m = mock(Message.class);
        var chat = mock(Chat.class);
        var user = mock(User.class);
        when(m.getChat()).thenReturn(chat);
        when(m.getFrom()).thenReturn(user);
        when(m.getChatId()).thenReturn(-12L);
        when(user.getId()).thenReturn(99L);
        when(m.getText()).thenReturn("/memory_off");
        when(client.execute(any(GetChatMember.class))).thenReturn(new ChatMemberMember());
        var commands = new MemoryCommands(memory, delivery);
        assertTrue(commands.handle(m, client));
        verify(memory, never()).setCapture(anyLong(), anyBoolean());
        when(client.execute(any(GetChatMember.class))).thenReturn(new ChatMemberAdministrator());
        when(memory.status(-12)).thenReturn("off");
        assertTrue(commands.handle(m, client));
        verify(memory).setCapture(-12, false);
    }

    @Test
    void ordinaryMessagesContinue() {
        var m = mock(Message.class);
        when(m.getText()).thenReturn("Маша, привет");
        assertFalse(
                new MemoryCommands(mock(ChatMemoryService.class), mock(TelegramDelivery.class))
                        .handle(m, mock(TelegramClient.class)));
    }
}
