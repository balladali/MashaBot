package ru.balladali.balladalibot.balladalibot.core.handlers.inline;

import org.telegram.telegrambots.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.api.objects.inlinequery.result.chached.InlineQueryResultCachedVideo;

import java.util.List;

public interface InlineHandler {
    List<? extends InlineQueryResult> answerInline(InlineQuery query);
}
