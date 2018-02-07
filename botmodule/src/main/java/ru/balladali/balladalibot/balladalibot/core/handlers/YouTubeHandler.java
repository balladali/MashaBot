package ru.balladali.balladalibot.balladalibot.core.handlers;

import com.google.common.base.Strings;
import ru.balladali.balladalibot.balladalibot.core.MessageHandler;
import ru.balladali.balladalibot.balladalibot.core.entity.MessageEntity;
import ru.balladali.balladalibot.balladalibot.core.services.YouTubeService;

import java.io.IOException;
import java.util.List;

public class YouTubeHandler implements MessageHandler{
    private YouTubeService youTubeService;

    public YouTubeHandler(YouTubeService youTubeService) {
        this.youTubeService = youTubeService;
    }

    @Override
    public String answer(MessageEntity entity) {
        String message = entity.getText();
        String chatId = entity.getChatId();
        if (needYouTubeSearch(message)) {
            String searchQuery = message.toLowerCase().replace("маша, найди", "").trim();
            if (Strings.isNullOrEmpty(searchQuery)) {
                return "Ну и чего искать-то?";
            } else {
                try {
                    List<String> foundVideos = youTubeService.search(searchQuery);
                    return foundVideos.get(0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private boolean needYouTubeSearch(String message) {
        return message.toLowerCase().contains("маша, найди");
    }

}
