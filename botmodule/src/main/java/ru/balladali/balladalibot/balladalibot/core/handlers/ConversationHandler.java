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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

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
            return getAnswer(message);
        }
        return null;
    }

    private boolean needAnswer(String message) {
        return message.contains("Маша") || message.contains("маша");
    }

    private String getAnswer(String message) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        long timestamp = System.currentTimeMillis();
        Random rand = new Random();

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("request", message);
        parameters.add("user_name", USER_NAME);
        parameters.add("bot_name", "pBot");
        parameters.add("dialog_lang", "ru");
//            parameters.add("dialog_id", "f757e541-58a2-4641-9075-7798df4a1260");
//            parameters.add("dialog_greeting", "false");
        parameters.add("a", PUBLIC_API);
        parameters.add("b", String.valueOf(Integer.toUnsignedLong(crc(timestamp + "b"))));
        parameters.add("c", String.valueOf(Integer.toUnsignedLong(getSign(timestamp))));
        parameters.add("d", String.valueOf(Integer.toUnsignedLong(crc(System.currentTimeMillis() + "d"))));
        parameters.add("e", String.format(Locale.ENGLISH, "%.15f", rand.nextFloat()));
        parameters.add("t", String.valueOf(timestamp));
        parameters.add("x", String.format(Locale.ENGLISH, "%.15f", rand.nextFloat() * 0xa));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(parameters, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(ANSWER_URL, request, String.class);
        String content = response.getBody();
        JSONObject json = new JSONObject(content);
        return json.getString(ANSWER_FIELD);
    }

    private int crc(String param) {
        List<Integer> b = abc();
        int c = -0x1;
        for (int k = 0; k < param.length(); k++) {
            c = c >>> 0x8 ^ b.get((c ^ param.charAt(k)) & 0xff);
        }
        return ~c;
    }

    private List<Integer> abc() {
        int a;
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            a = i;
            for (int j = 0; j < 8; j++) {
                a = (a & 0x1) == 1 ? 0xedb88320 ^ a >>> 0x1 : a >>> 0x1;
            }
            result.add(a);
        }
        return result;
    }

    private int getSign(long param) {
        return crc("public-api" + param + "4c153765f54c31ff" + "aa63c7fbf560553f5e3f428e877e2b0f");
    }
}
