package ru.balladali.mashabot.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.balladali.mashabot.core.services.KeyedTaskExecutor;

@Configuration
public class ProcessingConfiguration {
    @Bean(destroyMethod = "close")
    KeyedTaskExecutor messageExecutor(@Value("${bot.processing.workers:4}") int workers,
                                     @Value("${bot.processing.capacity:64}") int capacity,
                                     @Value("${bot.processing.per-dialog-capacity:8}") int perDialog) {
        return new KeyedTaskExecutor("message", workers, capacity, perDialog);
    }
    @Bean(destroyMethod = "close")
    KeyedTaskExecutor summaryExecutor(@Value("${bot.processing.summary-capacity:16}") int capacity) {
        return new KeyedTaskExecutor("summary", 1, capacity, 2);
    }
    @Bean(destroyMethod = "close")
    KeyedTaskExecutor notificationExecutor() { return new KeyedTaskExecutor("notification", 1, 16, 1); }
}
