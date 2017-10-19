package ru.balladali.balladalibot.balladalibot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.telegram.telegrambots.ApiContextInitializer;
import ru.balladali.balladalibot.balladalibot.telegram.BalladaliBot;

@SpringBootApplication
public class BalladaliBotApplication {
	@Autowired
	BalladaliBot balladaliBot;

	{
		ApiContextInitializer.init();
	}

	public static void main(String[] args) {
		SpringApplication.run(BalladaliBotApplication.class, args);
	}
}
