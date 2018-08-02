package ru.balladali.balladalibot.balladalibot.core.handlers.message;

import com.google.common.base.Strings;
import org.apache.commons.lang.StringUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.balladali.balladalibot.balladalibot.core.entity.YouTubeVideoEntity;
import ru.balladali.balladalibot.balladalibot.core.services.YouTubeService;
import ru.balladali.balladalibot.balladalibot.telegram.TelegramMessage;

import java.io.IOException;
import java.util.List;

public class YouTubeHandler implements MessageHandler {
    private YouTubeService youTubeService;

    public YouTubeHandler(YouTubeService youTubeService) {
        this.youTubeService = youTubeService;
    }

    @Override
    public void handle(TelegramMessage entity) {
        String message = entity.getText();
        if (needHandle(message)) {
            String searchQuery = message.toLowerCase().replace("маша, найди", "").trim();
            if (Strings.isNullOrEmpty(searchQuery)) {
                sendAnswer(entity, "Ну и чего искать-то?");
            } else {
                try {
                    List<YouTubeVideoEntity> foundVideos = youTubeService.search(searchQuery);
                    sendAnswer(entity, foundVideos.get(0).getLink());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public boolean needHandle(String message) {
        return StringUtils.containsIgnoreCase(message, "Маша, найди");
    }

    @Override
    public void sendAnswer(TelegramMessage messageEntity, String answer) {
        SendMessage sendMessage = new SendMessage(messageEntity.getChatId(), answer);
        try {
            messageEntity.getSender().execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
