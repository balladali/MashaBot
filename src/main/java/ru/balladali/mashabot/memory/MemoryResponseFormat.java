package ru.balladali.mashabot.memory;

import java.util.regex.Pattern;

/** Removes only the internal Telegram history header accidentally copied by the model. */
public final class MemoryResponseFormat {
    private static final Pattern HEADER = Pattern.compile(
            "\\A\\[\\d{4}-\\d{2}-\\d{2}T[^\\]\\r\\n]+Z\\] [^\\r\\n]*? "
                    + "\\(message=\\d+, reply=(?:null|\\d+), bot=true\\):[ \\t]*"
                    + "(?:\\\\(?:n)?[ \\t]*)?(?:\\r?\\n)?");

    private MemoryResponseFormat() {}

    public static String answerText(String text) {
        if (text == null) return null;
        return HEADER.matcher(text.strip()).replaceFirst("").strip();
    }
}
