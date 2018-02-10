package ru.balladali.balladalibot.balladalibot.core.handlers.message;

import com.google.common.base.Strings;
import org.apache.commons.lang.StringUtils;
import ru.balladali.balladalibot.balladalibot.core.entity.MessageEntity;
import ru.balladali.balladalibot.balladalibot.core.entity.YouTubeVideoEntity;
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
        if (needAnswer(message)) {
            String searchQuery = message.toLowerCase().replace("маша, найди", "").trim();
            if (Strings.isNullOrEmpty(searchQuery)) {
                return "Ну и чего искать-то?";
            } else {
                try {
                    List<YouTubeVideoEntity> foundVideos = youTubeService.search(searchQuery);
                    return foundVideos.get(0).getLink();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    @Override
    public boolean needAnswer(String message) {
        return StringUtils.containsIgnoreCase(message, "Маша, найди");
    }

}
