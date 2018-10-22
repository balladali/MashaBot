package ru.balladali.mashabot.core.handlers.inline;

import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;

import java.util.List;

public interface InlineHandler {

    List<? extends InlineQueryResult> answerInline(InlineQuery query);
}
