package ru.balladali.mashabot.core.handlers.inline;

import com.google.common.collect.Lists;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultVideo;
import ru.balladali.mashabot.core.entity.YouTubeVideoEntity;
import ru.balladali.mashabot.core.services.YouTubeService;

import java.io.IOException;
import java.util.List;

public class YouTubeInlineHandler implements InlineHandler {

    YouTubeService youTubeService;

    public YouTubeInlineHandler(YouTubeService youTubeService) {
        this.youTubeService = youTubeService;
    }

    @Override
    public List<? extends InlineQueryResult> answerInline(InlineQuery query) {
        try {
            List<YouTubeVideoEntity> videoIDs = youTubeService.search(query.getQuery());
            List<InlineQueryResultVideo> results = Lists.newArrayList();
            for (YouTubeVideoEntity video: videoIDs) {
                results.add(getInlineQueryResultVideo(video));
            }
            return results;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private InlineQueryResultVideo getInlineQueryResultVideo(YouTubeVideoEntity video) {
        InlineQueryResultVideo inlineQueryResultVideo = new InlineQueryResultVideo();
        inlineQueryResultVideo.setId(video.getId());
        inlineQueryResultVideo.setMimeType("text/html");
        inlineQueryResultVideo.setVideoUrl(video.getLink());
        inlineQueryResultVideo.setInputMessageContent(getInputTextMessageContent(video.getLink()));
        inlineQueryResultVideo.setTitle(video.getTitle());
        inlineQueryResultVideo.setThumbUrl(video.getThumbUrl());
        inlineQueryResultVideo.setDescription(video.getDescription());
        inlineQueryResultVideo.setVideoDuration(video.getDuration());
        return inlineQueryResultVideo;
    }

    private InputMessageContent getInputTextMessageContent(String link) {
        InputTextMessageContent inputTextMessageContent = new InputTextMessageContent();
        inputTextMessageContent.setMessageText(link);
        return inputTextMessageContent;
    }
}
