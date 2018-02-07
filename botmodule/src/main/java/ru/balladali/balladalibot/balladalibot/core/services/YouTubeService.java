package ru.balladali.balladalibot.balladalibot.core.services;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class YouTubeService {
    @Value("${credential.youtube.key}")
    private String YOUTUBE_API_KEY;

    private final String YOUTUBE_LINK = "https://www.youtube.com/watch?v=";
    private YouTube youTube;

    public YouTubeService(YouTube youTube) {
        this.youTube = youTube;
    }

    public List<String> search(String searchQuery) throws IOException {
        YouTube.Search.List search = youTube.search().list("snippet");
        search.setQ(searchQuery);
        search.setType("video");
        search.setKey(YOUTUBE_API_KEY);
        SearchListResponse searchListResponse = search.execute();

        List<String> videos = new ArrayList<>();

        if (searchListResponse != null) {
            for (SearchResult searchResult: searchListResponse.getItems()) {
                videos.add(YOUTUBE_LINK + searchResult.getId().getVideoId());
            }
        }

        return videos;
    }
}
