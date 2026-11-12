package ru.balladali.mashabot.core.handlers.message;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import ru.balladali.mashabot.telegram.TelegramDelivery;
import ru.balladali.mashabot.telegram.TelegramMessage;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only a complete, single binary expression is considered a calculation request. */
public class CalculatorHandler implements MessageHandler {
    private static final String NUMBER = "[+-]?[0-9]{1,30}(?:[.,][0-9]{1,30})?";
    private static final Pattern FORMULA = Pattern.compile(
            "\\s*(" + NUMBER + ")\\s*([+*/×÷−-])\\s*(" + NUMBER + ")\\s*[=?]?\\s*");
    private final TelegramDelivery delivery;

    public CalculatorHandler(TelegramDelivery delivery) {
        this.delivery = delivery;
    }

    private Matcher match(String text) {
        return text == null || text.length() > 160 ? null : FORMULA.matcher(text);
    }

    @Override
    public boolean needHandle(TelegramMessage message) {
        Matcher matcher = match(message.getText());
        return matcher != null && matcher.matches();
    }

    @Override
    public void handle(TelegramMessage message) {
        Matcher matcher = match(message.getText());
        if (matcher == null || !matcher.matches()) return;
        BigDecimal left = new BigDecimal(matcher.group(1).replace(',', '.'));
        BigDecimal right = new BigDecimal(matcher.group(3).replace(',', '.'));
        String operator = matcher.group(2);
        if ((operator.equals("/") || operator.equals("÷")) && right.signum() == 0) {
            sendAnswer(message, "На ноль делить нельзя.");
            return;
        }
        boolean approximate = false;
        BigDecimal result;
        switch (operator) {
            case "+" -> result = left.add(right);
            case "-", "−" -> result = left.subtract(right);
            case "*", "×" -> result = left.multiply(right);
            default -> {
                try {
                    result = left.divide(right);
                } catch (ArithmeticException repeatingDecimal) {
                    result = left.divide(right, MathContext.DECIMAL64);
                    approximate = true;
                }
            }
        }
        sendAnswer(message, left.stripTrailingZeros().toPlainString() + " " + operator + " "
                + right.stripTrailingZeros().toPlainString() + (approximate ? " ≈ " : " = ")
                + result.stripTrailingZeros().toPlainString());
    }

    @Override
    public void sendAnswer(TelegramMessage message, String answer) {
        SendMessage response = new SendMessage(message.getChatId(), answer);
        response.setReplyToMessageId(message.getMessage().getMessageId());
        delivery.send(message.getClient(), response);
    }
}
