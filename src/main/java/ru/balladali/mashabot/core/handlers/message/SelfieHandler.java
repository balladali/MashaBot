package ru.balladali.mashabot.core.handlers.message;

import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.balladali.mashabot.core.services.SelfieService;
import ru.balladali.mashabot.telegram.TelegramMessage;

import java.io.ByteArrayInputStream;
import java.util.regex.Pattern;

public class SelfieHandler implements MessageHandler {

    private static final Pattern BOT_TRIGGER = Pattern.compile("^(?:маша[\\s,:-]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SELFIE_TRIGGER = Pattern.compile("(селфи|selfie|фотк|фото\\s*меня|сфотк)" , Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final SelfieService selfieService;

    public SelfieHandler(SelfieService selfieService) {
        this.selfieService = selfieService;
    }

    @Override
    public void handle(TelegramMessage entity) {
        String request = extractRequest(entity != null ? entity.getText() : null);

        if (!selfieService.hasReference()) {
            sendAnswer(entity, "Я бы с радостью, но сейчас у меня нет референса внешности 🙈");
            return;
        }

        if (!selfieService.canGenerateNow()) {
            sendAnswer(entity, "Я сейчас занята и не могу сфоткаться 🙏");
            return;
        }

        sendTyping(entity);
        sendAnswer(entity, "Подожди минуточку, сейчас сфоткаюсь 📸");

        try {
            byte[] image = selfieService.generate(request);
            sendPhoto(entity, image, "Держи 💫");
        } catch (Exception e) {
            e.printStackTrace();
            sendAnswer(entity, "Я сейчас занята и не могу сфоткаться 🙏");
        }
    }

    @Override
    public boolean needHandle(TelegramMessage message) {
        if (message == null || message.getText() == null) return false;
        String text = message.getText();
        return BOT_TRIGGER.matcher(text).find() && SELFIE_TRIGGER.matcher(text).find();
    }

    @Override
    public void sendAnswer(TelegramMessage messageEntity, String answer) {
        if (messageEntity == null || answer == null || answer.isBlank()) return;
        SendMessage msg = new SendMessage(messageEntity.getChatId(), answer.strip());
        try {
            messageEntity.getClient().execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendPhoto(TelegramMessage messageEntity, byte[] imageBytes, String caption) {
        if (messageEntity == null || imageBytes == null || imageBytes.length == 0) {
            sendAnswer(messageEntity, "Я сейчас занята и не могу сфоткаться 🙏");
            return;
        }

        SendPhoto photo = new SendPhoto(
                messageEntity.getChatId(),
                new InputFile(new ByteArrayInputStream(imageBytes), "selfie.jpg")
        );
        photo.setCaption(caption);
        try {
            messageEntity.getClient().execute(photo);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendAnswer(messageEntity, "Я сейчас занята и не могу сфоткаться 🙏");
        }
    }

    private void sendTyping(TelegramMessage messageEntity) {
        SendChatAction action = new SendChatAction(messageEntity.getChatId(), ActionType.TYPING.toString());
        try {
            messageEntity.getClient().execute(action);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private static String extractRequest(String text) {
        if (text == null) return "";
        String t = BOT_TRIGGER.matcher(text).replaceFirst("").trim();
        return t.replaceAll("(?i)(селфи|selfie|фотк\\w*|фото\\s*меня|сфотк\\w*)", "").trim();
    }
}
