package ru.balladali.balladalibot.balladalibot.core.services;

import com.google.api.client.util.Joiner;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;

import com.google.common.collect.Lists;

import org.springframework.beans.factory.annotation.Value;

import ru.balladali.balladalibot.balladalibot.core.entity.YouTubeVideoEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class YouTubeService {
    @Value("${credential.youtube.key}")
    private String YOUTUBE_API_KEY;

    @Value("${search.youtube.maxresults}")
    private long MAX_SEARCH_RESULTS;

    private YouTube youTube;

    public YouTubeService(YouTube youTube) {
        this.youTube = youTube;
    }

    public List<YouTubeVideoEntity> search(String searchQuery) throws IOException {
        YouTube.Search.List search = youTube.search().list("snippet").setKey(YOUTUBE_API_KEY);
        search.setQ(searchQuery);
        search.setType("video");
        search.setMaxResults(MAX_SEARCH_RESULTS);
        SearchListResponse searchListResponse = search.execute();

        YouTube.Videos.List info = youTube.videos().list("snippet, contentDetails").setKey(YOUTUBE_API_KEY);
        info.setMaxResults(MAX_SEARCH_RESULTS);

        List<YouTubeVideoEntity> videos = new ArrayList<>();

        if (searchListResponse != null) {
            List<String> videoIds = Lists.newArrayList();
            for (SearchResult searchResult: searchListResponse.getItems()) {
                videoIds.add(searchResult.getId().getVideoId());
            }
            Joiner stringJoiner = Joiner.on(',');
            String videoId = stringJoiner.join(videoIds);

            info.setId(videoId);
            VideoListResponse videoListResponse = info.execute();
            for (Video video: videoListResponse.getItems()) {
                videos.add(new YouTubeVideoEntity(video));
            }
        }

        return videos;
    }
}
