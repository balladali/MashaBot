package ru.balladali.mashabot.memory;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("memory")
public class MemoryProperties {
    private boolean enabled = true;
    private Capture capture = new Capture();
    private Context context = new Context();
    private String url = "jdbc:postgresql://localhost:5432/masha";
    private String username = "masha";
    private String password = "";

    @Data
    public static class Capture {
        private boolean enabled = true;
        private boolean defaultChatEnabled = false;
    }

    @Data
    public static class Context {
        private int recentLimit = 30;
        private int replyDepth = 5;
        private int maxMessages = 80;
        private int maxCharacters = 30000;
    }

    public void validate() {
        if (context.recentLimit < 1
                || context.recentLimit > 1000
                || context.replyDepth < 0
                || context.replyDepth > 100
                || context.maxMessages < 1
                || context.maxMessages > 1000
                || context.maxCharacters < 256
                || context.maxCharacters > 1000000)
            throw new IllegalArgumentException("Invalid memory context limits");
    }
}
