package ru.balladali.mashabot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import ru.balladali.mashabot.pingjob.PingJob;
import ru.balladali.mashabot.telegram.Bot;

@SpringBootApplication
@ComponentScan
public class BotApplication {

	@Autowired
	private Bot bot;

	public static void main(String[] args) {
		SpringApplication.run(BotApplication.class, args);
	}

	@Bean
	@ConditionalOnProperty(name = "ping-job.enabled", havingValue = "true")
	public PingJob getPing() {
		return new PingJob();
	}
}
