package ru.balladali.balladalibot.balladalibot.core.entity;

import com.google.api.services.youtube.model.Video;
import lombok.Data;

import org.joda.time.Period;
import org.joda.time.Seconds;
import org.joda.time.format.ISOPeriodFormat;
import org.joda.time.format.PeriodFormatter;

@Data
public class YouTubeVideoEntity {

    private String id;
    private String title;
    private String thumbUrl;
    private String description;
    private String link;
    private int duration;

    public YouTubeVideoEntity(Video video) {
        this.id = video.getId();
        this.title = video.getSnippet().getTitle();
        this.thumbUrl = video.getSnippet().getThumbnails().getDefault().getUrl();
        this.description = video.getSnippet().getDescription();
        this.duration = convertDuration(video.getContentDetails().getDuration());
        this.link = "https://www.youtube.com/watch?v=" + this.id;
    }

    private int convertDuration(String duration) {
        PeriodFormatter formatter = ISOPeriodFormat.standard();
        Period p = formatter.parsePeriod(duration);
        Seconds s = p.toStandardSeconds();
        return s.getSeconds();
    }
}
