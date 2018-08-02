package ru.balladali.balladalibot.balladalibot.core.services;

import java.io.IOException;
import java.io.InputStream;

public interface SpeechService {

    InputStream synthesize(String text) throws IOException;

}
