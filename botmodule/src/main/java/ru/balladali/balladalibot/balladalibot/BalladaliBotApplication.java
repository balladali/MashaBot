package ru.balladali.balladalibot.balladalibot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.telegram.telegrambots.ApiContextInitializer;
import ru.balladali.balladalibot.balladalibot.pingjob.PingJob;
import ru.balladali.balladalibot.balladalibot.telegram.BalladaliBot;

import javax.annotation.PostConstruct;

@SpringBootApplication
@ComponentScan
public class BalladaliBotApplication {

	@Autowired
	private	BalladaliBot balladaliBot;

	public static void main(String[] args) {
		ApiContextInitializer.init();
		SpringApplication.run(BalladaliBotApplication.class, args);
	}

	@Bean
	public PingJob getPing() {
		return new PingJob();
	}

	@PostConstruct
	private void init() {
		balladaliBot.init();
	}
}
