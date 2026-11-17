package ru.balladali.mashabot.memory;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import ru.balladali.mashabot.telegram.TelegramDelivery;

@Component
public class MemoryCommands {
    private final ChatMemoryService memory;
    private final TelegramDelivery delivery;

    public MemoryCommands(ChatMemoryService memory, TelegramDelivery delivery) {
        this.memory = memory;
        this.delivery = delivery;
    }

    public boolean handle(Message m, TelegramClient client) {
        String token = m.getText() == null ? "" : m.getText().strip().split("\\s+", 2)[0];
        String command = token.split("@", 2)[0];
        if (!command.matches("/memory_(on|off|status)")) return false;
        try {
            if (token.contains("@")
                    && !token.substring(token.indexOf('@') + 1)
                            .equalsIgnoreCase(client.execute(new GetMe()).getUserName()))
                return true;
            String answer;
            boolean allowed = false;
            if (m.getFrom() != null && m.getSenderChat() == null) {
                if (m.getChat().isUserChat()) allowed = m.getChatId().equals(m.getFrom().getId());
                else {
                    String status =
                            client.execute(
                                            new GetChatMember(
                                                    m.getChatId().toString(), m.getFrom().getId()))
                                    .getStatus();
                    allowed = status.equals("creator") || status.equals("administrator");
                }
            }
            if (command.equals("/memory_status")) answer = memory.status(m.getChatId());
            else if (!allowed) answer = "Настройку памяти может менять только администратор чата.";
            else {
                memory.setCapture(m.getChatId(), command.equals("/memory_on"));
                answer = memory.status(m.getChatId());
            }
            var send = new SendMessage(m.getChatId().toString(), answer);
            send.setMessageThreadId(m.getMessageThreadId());
            delivery.send(client, send);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn("event=memory_command_failed type={}", e.getClass().getSimpleName());
        }
        return true;
    }
}
