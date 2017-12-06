package ru.balladali.balladalibot.balladalibot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.telegram.telegrambots.ApiContextInitializer;
import ru.balladali.balladalibot.balladalibot.pingjob.PingJob;
import ru.balladali.balladalibot.balladalibot.telegram.BalladaliBot;

@SpringBootApplication
@ComponentScan
public class BalladaliBotApplication {
	@Autowired
	BalladaliBot balladaliBot;

	{
		ApiContextInitializer.init();
	}

	public static void main(String[] args) {
		SpringApplication.run(BalladaliBotApplication.class, args);
	}

	@Bean
	public PingJob getPing() {
		return new PingJob();
	}
}
