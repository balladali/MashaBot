package ru.balladali.mashabot.core.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BotContext {
    @Builder.Default
    private String request1 = "";

    @Builder.Default
    private String answer1 = "";

    @Builder.Default
    private String request2 = "";

    @Builder.Default
    private String answer2 = "";

    @Builder.Default
    private String request3 = "";

    @Builder.Default
    private String answer3 = "";
}
