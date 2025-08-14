package ru.balladali.mashabot.core.handlers.message;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.balladali.mashabot.core.clients.exchange.ExchangeRateClient;
import ru.balladali.mashabot.core.services.CurrencyMorphNormalizerAot;
import ru.balladali.mashabot.telegram.TelegramMessage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ищет в тексте суммы с валютой (ISO/символы/EN/русские формы), конвертирует в RUB и отвечает.
 */
public class CurrencyConvertHandler implements MessageHandler {

    private final ExchangeRateClient rateClient;
    private final CurrencyMorphNormalizerAot ruMorph;

    // быстрый словарь: ISO, символы, EN-термины, жаргон RU (русские падежи ловим морфологией)
    private static final Map<String, String> TOKEN_TO_ISO = new HashMap<>();
    static {
        // ISO базовые
        for (String c : Arrays.asList(
                "USD","EUR","GBP","JPY","CNY","KZT","TRY","AED","UAH","GEL","BYN","PLN","CHF","SEK","NOK",
                "AMD","AZN","KGS","UZS","TJS","ILS","INR","CAD","AUD","HKD","SGD","DKK","CZK","HUF","RON","ISK"
        )) TOKEN_TO_ISO.put(c.toLowerCase(Locale.ROOT), c);

        // Symbols
        TOKEN_TO_ISO.put("$", "USD");
        TOKEN_TO_ISO.put("€", "EUR");
        TOKEN_TO_ISO.put("£", "GBP");
        TOKEN_TO_ISO.put("¥", "JPY");
        TOKEN_TO_ISO.put("₸", "KZT");
        TOKEN_TO_ISO.put("₺", "TRY");
        TOKEN_TO_ISO.put("₴", "UAH");
        TOKEN_TO_ISO.put("₣", "CHF");
        TOKEN_TO_ISO.put("₽", "RUB");

        // EN слова
        mapEn("dollar", "USD", "dollars","usd","us dollar","us dollars","u.s. dollar","u.s. dollars","bucks","buck");
        mapEn("euro",   "EUR", "euros","eur");
        mapEn("pound",  "GBP", "pounds","gbp","pound sterling","sterling");
        mapEn("yen",    "JPY", "jpy","japanese yen");
        mapEn("yuan",   "CNY", "cny","renminbi","rmb","chinese yuan");
        mapEn("lira",   "TRY", "try","turkish lira","turkish liras");
        mapEn("dirham", "AED", "aed","uae dirham","uae dirhams");
        mapEn("hryvnia","UAH", "uah","ukrainian hryvnia","hryvnias");
        mapEn("lari",   "GEL", "gel","georgian lari");
        mapEn("zloty",  "PLN", "pln","polish zloty","zlotys","zlote","zlotych");
        mapEn("franc",  "CHF", "chf","swiss franc","swiss francs");
        mapEn("krona",  "SEK", "sek","swedish krona","kronor","krona");
        mapEn("krone",  "NOK", "nok","norwegian krone","kroner");
        mapEn("ruble",  "RUB", "rub","rouble","roubles");
        // RU жаргон, если попадёт без морфологии
        TOKEN_TO_ISO.put("бакс", "USD");
        TOKEN_TO_ISO.put("баксов", "USD");
        TOKEN_TO_ISO.put("зелень", "USD");
    }
    private static void mapEn(String base, String iso, String... forms) {
        TOKEN_TO_ISO.put(base.toLowerCase(Locale.ROOT), iso);
        for (String f : forms) TOKEN_TO_ISO.put(f.toLowerCase(Locale.ROOT), iso);
    }

    // число: 1 234,56 | 1,234.56 | 1234.56 | 1234
    private static final String AMOUNT = "(?:\\d{1,3}(?:[\\s\\u00A0.]\\d{3})*(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?)";
    // валюта: ISO3 | символ | слово/фраза (до 30 букв/пробелов/дефисов)
    private static final String CUR    = "(?:[A-Za-z]{3}|[$€£¥₸₺₴₽]|[\\p{L}][\\p{L}\\s\\-]{1,30})";

    private static final Pattern P = Pattern.compile(
            "(?i)(?:" +
                    "(?<cur1>" + CUR + ")\\s*~?\\s*(?<amount1>" + AMOUNT + ")" +
                    ")|" +
                    "(?:" +
                    "(?<amount2>" + AMOUNT + ")\\s*~?\\s*(?<cur2>" + CUR + ")" +
                    ")"
    );

    public CurrencyConvertHandler(ExchangeRateClient rateClient) {
        this(rateClient, CurrencyMorphNormalizerAot.defaultRu());
    }
    public CurrencyConvertHandler(ExchangeRateClient rateClient, CurrencyMorphNormalizerAot ruMorph) {
        this.rateClient = rateClient;
        this.ruMorph = ruMorph;
    }

    @Override
    public void handle(TelegramMessage entity) {
        String text = Optional.ofNullable(entity.getText()).orElse("");
        // можно также смотреть в reply при необходимости:
        // if (text.isBlank() && entity.getReply() != null) text = entity.getReply();

        List<FoundMoney> found = extractMentions(text);
        if (found.isEmpty()) return; // не наш кейс — пусть другие хэндлеры обработают

        DecimalFormat df = new DecimalFormat("#,##0.00");
        List<String> lines = new ArrayList<>();

        for (FoundMoney fm : found) {
            if ("RUB".equals(fm.iso)) continue; // рубли не конвертируем
            try {
                BigDecimal rate = rateClient.rateToRub(fm.iso);
                if (rate == null) continue;
                BigDecimal rub = fm.amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                lines.add(df.format(fm.amount) + " " + fm.iso + " ≈ " +
                        df.format(rub) + " RUB (курс " + df.format(rate) + ")");
            } catch (Exception e) {
                // проглотим, внизу общий ответ
            }
        }

        if (lines.isEmpty()) {
            sendAnswer(entity, "Извини, не смогла определить валюту или курс сейчас недоступен :(");
        } else {
            sendAnswer(entity, String.join("\n", lines));
        }
    }

    private static boolean isRub(String iso) {
        return "RUB".equalsIgnoreCase(iso);
    }

    @Override
    public boolean needHandle(String message) {
        if (message == null || message.isEmpty()) return false;
        Matcher m = P.matcher(message);
        while (m.find()) {
            String curToken = firstNonNull(m.group("cur1"), m.group("cur2"));
            if (curToken == null) continue;
            String iso = normalizeCurrency(curToken);
            if (iso != null && !isRub(iso)) return true; // найдена валюта ≠ RUB
        }
        return false; // были только рубли или ничего
    }

    @Override
    public void sendAnswer(TelegramMessage messageEntity, String answer) {
        SendMessage sendMessage = new SendMessage(messageEntity.getChatId(), answer);
        try {
            messageEntity.getClient().execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /* ===================== helpers ===================== */

    private static class FoundMoney {
        final BigDecimal amount;
        final String iso;
        FoundMoney(BigDecimal a, String iso) { this.amount = a; this.iso = iso; }
    }

    private List<FoundMoney> extractMentions(String text) {
        Matcher m = P.matcher(text);
        List<FoundMoney> out = new ArrayList<>();
        while (m.find()) {
            String curToken = firstNonNull(m.group("cur1"), m.group("cur2"));
            String amtToken = firstNonNull(m.group("amount1"), m.group("amount2"));
            if (curToken == null || amtToken == null) continue;

            String iso = normalizeCurrency(curToken);
            if (iso == null || isRub(iso)) continue;

            BigDecimal amount = parseAmount(amtToken);
            if (amount == null) continue;

            out.add(new FoundMoney(amount, iso));
        }
        return out;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static BigDecimal parseAmount(String token) {
        if (token == null) return null;
        String s = token.replace('\u00A0',' ').trim();
        int lastComma = s.lastIndexOf(',');
        int lastDot = s.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            if (lastComma > lastDot) {
                s = s.replace(".", "").replace(',', '.'); // 1.234,56 -> 1234.56
            } else {
                s = s.replace(",", "");                   // 1,234.56 -> 1234.56
            }
        } else if (lastComma >= 0) {
            s = s.replace(" ", "").replace(',', '.');     // 1 234,56 -> 1234.56
        } else {
            s = s.replace(" ", "");                       // 1 234 -> 1234
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeCurrency(String token) {
        if (token == null) return null;
        String t = token.trim().toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("[\\s\\u00A0]+", " ")
                .trim();

        // 0) быстрый словарь (ISO/символы/EN/жаргон)
        String direct = TOKEN_TO_ISO.get(t);
        if (direct != null) return direct;

        // 0.1) эвристика для «крона/кроны/крон» + указание страны (работает и для "кроны Норвегии")
        String kronaIso = resolveKronaPhrase(t);
        if (kronaIso != null) return kronaIso;

        // 1) морфология RU (ловит «долларов», «швейцарских франков» и т.п.)
        String ruIso = ruMorph.toIso(t);   // <-- как и было
        if (ruIso != null) return ruIso;

        // 2) ISO-3
        if (t.matches("^[a-z]{3}$")) return t.toUpperCase(Locale.ROOT);

        // 3) англ. множественное
        if (t.matches("^[a-z]+s$")) {
            String singular = t.substring(0, t.length() - 1);
            String iso = TOKEN_TO_ISO.get(singular);
            if (iso != null) return iso;
        }
        return null;
    }

    /** Простая эвристика для «крона/кроны/крон» с указанием страны/прилагательного. */
    private static String resolveKronaPhrase(String t) {
        if (!t.contains("крон")) return null; // не похоже на «крона/кроны/крон»

        // прилагательные
        if (t.contains("норвеж")) return "NOK";
        if (t.contains("швед"))   return "SEK";
        if (t.contains("датск"))  return "DKK";
        if (t.contains("чеш"))    return "CZK";
        if (t.contains("исланд")) return "ISK";

        // страны в разных падежах: «кроны Норвегии/Швеции/Дании/Чехии/Исландии»
        if (t.matches(".*\\bнорвег(ия|ии|ию|ией)\\b.*")) return "NOK";
        if (t.matches(".*\\bшвеци(я|и|ю|ей)\\b.*"))     return "SEK";
        if (t.matches(".*\\bдани(я|и|ю|ей)\\b.*"))      return "DKK";
        if (t.matches(".*\\bчехи(я|и|ю|ей)\\b.*"))      return "CZK";
        if (t.matches(".*\\bисланди(я|и|ю|ей)\\b.*"))   return "ISK";

        return null; // без уточнения — оставляем неоднозначным
    }

}

