package ru.balladali.balladalibot.balladalibot.core.handlers;

import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import ru.balladali.balladalibot.balladalibot.core.MessageEntity;
import ru.balladali.balladalibot.balladalibot.core.MessageHandler;

public class ConversationHandler implements MessageHandler {

    private final String ANSWER_URL = "http://p-bot.ru/api/getAnswer";
    private final String PUBLIC_API = "public-api";
    private final String USER_NAME = "Masha";
    private final String ANSWER_FIELD = "answer";

    @Override
    public String answer(MessageEntity entity) {
        String message = entity.getText();
        if (needAnswer(message)) {
            message = message.replaceAll("Маша, ", "").replaceAll("маша, ", "");
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//            headers.set("Host","p-bot.ru");
//            headers.set("Origin", "http://p-bot.ru");
//            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.94 Safari/537.36");
//            headers.set("Accept","*/*");
//            headers.set("Referer", "http://p-bot.ru/");
//            headers.set("Cookie", "dialog_id=f757e541-58a2-4641-9075-7798df4a1260; last_visit=1512564587730::1512578987730; dialog_sentiment=1");

            MultiValueMap<String, String> parameters = new LinkedMultiValueMap<String, String>();
            parameters.add("request", message);
//            parameters.add("request_1", "");
//            parameters.add("answer_1", "");
//        parameters.add("request_2", "");
//        parameters.add("answer_2", "");
//        parameters.add("request_3", "");
//        parameters.add("answer_3", "");
            parameters.add("user_name", USER_NAME);
            parameters.add("bot_name", "pBot");
            parameters.add("dialog_lang", "ru");
            parameters.add("dialog_id", "f757e541-58a2-4641-9075-7798df4a1260");
            parameters.add("dialog_greeting", "false");
            parameters.add("a", PUBLIC_API);
            parameters.add("b", "3711164143");
            parameters.add("c", "3329552147");
            parameters.add("d", "877689818");
            parameters.add("e", "0.5342009719764045");
            parameters.add("t", "1512583469595");
            parameters.add("x", "9.891452256115041");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(parameters, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(ANSWER_URL, request, String.class);
            String content = response.getBody();
            JSONObject json = new JSONObject(content);
            return json.getString(ANSWER_FIELD);
        }
        return null;
    }

    private boolean needAnswer(String message) {
        return message.contains("Маша") || message.contains("маша");
    }
}
