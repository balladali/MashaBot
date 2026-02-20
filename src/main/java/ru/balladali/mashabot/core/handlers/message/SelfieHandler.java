package ru.balladali.mashabot.core.handlers.message;

import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.balladali.mashabot.core.services.SelfieService;
import ru.balladali.mashabot.telegram.TelegramMessage;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.regex.Pattern;

public class SelfieHandler implements MessageHandler {

    private static final Pattern BOT_TRIGGER = Pattern.compile("^(?:маша[\\s,:-]*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SELFIE_TRIGGER = Pattern.compile("(селфи|selfie|фотк|фото\\s*меня|сфотк)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static volatile Long BOT_ID = null;

    private final SelfieService selfieService;

    public SelfieHandler(SelfieService selfieService) {
        this.selfieService = selfieService;
    }

    @Override
    public void handle(TelegramMessage entity) {
        String explicitRequest = extractRequest(entity != null ? entity.getText() : null);
        String contextualRequest = buildContextualRequest(entity, explicitRequest);

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
            byte[] image = selfieService.generate(contextualRequest);
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
        if (!SELFIE_TRIGGER.matcher(text).find()) return false;

        boolean hasBotTrigger = BOT_TRIGGER.matcher(text).find();
        return hasBotTrigger || isReplyToMashaMessage(message);
    }

    private boolean isReplyToMashaMessage(TelegramMessage telegramMessage) {
        Message message = telegramMessage.getMessage();
        if (message == null || message.getReplyToMessage() == null || message.getReplyToMessage().getFrom() == null) {
            return false;
        }

        Long mashaId = resolveBotId(telegramMessage);
        if (mashaId == null) return false;

        return Objects.equals(message.getReplyToMessage().getFrom().getId(), mashaId);
    }

    private Long resolveBotId(TelegramMessage telegramMessage) {
        if (BOT_ID != null) return BOT_ID;
        try {
            User me = telegramMessage.getClient().execute(new GetMe());
            if (me != null && me.getId() != null) {
                BOT_ID = me.getId();
                return BOT_ID;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String buildContextualRequest(TelegramMessage entity, String explicitRequest) {
        if (explicitRequest != null && !explicitRequest.isBlank()) {
            return explicitRequest;
        }

        if (entity != null && entity.getMessage() != null && entity.getMessage().getReplyToMessage() != null) {
            Message replied = entity.getMessage().getReplyToMessage();
            String repliedText = replied.getText();
            String repliedCaption = replied.getCaption();

            StringBuilder context = new StringBuilder();
            if (repliedText != null && !repliedText.isBlank()) {
                context.append(repliedText.strip());
            }
            if (repliedCaption != null && !repliedCaption.isBlank()) {
                if (!context.isEmpty()) context.append(" ");
                context.append(repliedCaption.strip());
            }

            if (!context.isEmpty()) {
                return "Сделай селфи, которое соответствует контексту диалога: " + context;
            }
        }

        return "Нейтральное реалистичное селфи в повседневной обстановке";
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
