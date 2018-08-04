package ru.balladali.balladalibot.balladalibot.pingjob;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

public class PingJob {
    private final TaskScheduler scheduler;
    public static int ITER = 0;
    private static final long DELAY_TO_UPDATE = TimeUnit.MINUTES.toMillis(25);

    public PingJob() {
        this.scheduler = new ConcurrentTaskScheduler();
    }

    @PostConstruct
    private void job() {
        scheduler.scheduleWithFixedDelay(new Runnable() {
            private int num = ITER;

            @Override
            public void run() {
                num = ITER;
                if (num < 48) {
                    try {
                        new RestTemplate().getForObject(new URI("https://balladalibot.herokuapp.com"), String.class);
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                    ITER++;
                }
            }
        }, DELAY_TO_UPDATE);
    }
}
