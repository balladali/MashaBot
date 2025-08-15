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
 * Конвертирует упоминания валюты в тексте/подписи сообщения в RUB.
 * - поддержка пересланных/подписей (caption), reply-текста и подписи в reply;
 * - игнорирует RUB (российские рубли), но конвертирует BYN → RUB;
 * - валюты стран бывшего СССР + базовые мировые;
 * - неоднозначности: крона (NOK/SEK/DKK/CZK/ISK), манат (AZN/TMT), лей (MDL/RON), рубль (BYN/RUB);
 * - множители: к/кк/k/kk/тыс/млн/млрд (и «без пробела»: 100к, 2млн, 3кк);
 * - пост-фильтр разрыва (не ловим «3 минуты … долларов»).
 */
public class CurrencyConvertHandler implements MessageHandler {

    private final ExchangeRateClient rateClient;
    private final CurrencyMorphNormalizerAot ruMorph;

    public CurrencyConvertHandler(ExchangeRateClient rateClient) {
        this.rateClient = rateClient;
        this.ruMorph = CurrencyMorphNormalizerAot.defaultRu();
        initTokenMap();
    }

    /* ================== Регэксп + пост-фильтр ================== */

    // число: 1 234,56 | 1,234.56 | 1234.56 | 1234
    private static final String AMOUNT = "(?:\\d{1,3}(?:[\\s\\u00A0.]\\d{3})*(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?)";
    // множитель через пробел: к/кк/тыс/млн/млрд/миллион/миллиард
    private static final String MULT = "(?:кк|к|тыс\\.?|тысяч[аи]?|млн\\.?|миллион(?:а|ов)?|млрд\\.?|миллиард(?:а|ов)?)";
    // множитель суффиксом без пробела: к/кк/k/kk/млн/млрд
    private static final String MULT_SUFFIX = "(?:кк|kk|к|k|млн|млрд)";

    // FIX: Валютное слово (финальное) RU/EN — якорим конец фразы валюты на реальное название валюты
    private static final String CUR_WORD_RU =
            "(?:доллар(?:[а-я]+)?|евро|фунт(?:[а-я]+)?|франк(?:[а-я]+)?|й?ен(?:а|ы|е|у|ой|ами|ах)?|юан(?:ь|я|ю|ем|ей|ями|ях)?|"
                    + "злот(?:ый|ых|ые|ого|ому|ым|ыми)?|лари|тенге|крон(?:а|ы|е|у|ой|ами|ах)?|манат(?:[а-я]+)?|"
                    + "ле[йюя]|леев|рубл(?:ь|я|ей|ю|ем|ями|ях)?|сомони|сом(?:[а-я]+)?|драм(?:[а-я]+)?|гривн(?:а|ы|е|у|ой|ами|ах)?|сум(?:[а-я]+)?)";

    private static final String CUR_WORD_EN =
            "(?:dollars?|euros?|pounds?|francs?|yen|yuan|zloty|zloties|lari|tenge|krona|kronor|kroner|manat|lei|rubles?)";

    // FIX: Фраза валюты — до 3 слов слева (прилагательные/страна) + само валютное слово; либо ISO3; либо символ
    private static final String CUR =
            "(?:[A-Za-z]{3}|[$€£¥₸₺₴₽]|(?:[\\p{L}\\-]+\\s+){0,3}(?:" + CUR_WORD_RU + "|" + CUR_WORD_EN + "))";

    private static final Pattern P = Pattern.compile(
            "(?i)(?:" +
                    // валюта → число (без посторонних слов между ними)
                    "(?<cur1>" + CUR + ")\\s*~?\\s*(?<amount1>" + AMOUNT + ")(?<mult1suf>" + MULT_SUFFIX + ")?(?:\\s+(?<mult1>" + MULT + "))?" +
                    ")|" +
                    // число → валюта
                    "(?:" +
                    "(?<amount2>" + AMOUNT + ")(?<mult2suf>" + MULT_SUFFIX + ")?(?:\\s+(?<mult2>" + MULT + "))?\\s*~?\\s*(?<cur2>" + CUR + ")" +
                    ")"
    );

    // Слова в разрыве между числом и валютой, из-за которых матч отклоняем (время/прочие единицы)
    private static final Set<String> GAP_STOPWORDS = Set.of(
            "секунда","секунды","секунд","сек","с",
            "минута","минуты","минут","мин",
            "час","часа","часов",
            "день","дня","дней",
            "неделя","недели","недель",
            "месяц","месяца","месяцев",
            "год","года","лет",
            "метр","метра","метров","км","кг",
            "шт","штук","процент","процента","процентов"
    );

    private static boolean gapIsSafe(String fullText, int leftEnd, int rightStart) {
        if (rightStart <= leftEnd) return true;
        String gap = fullText.substring(leftEnd, rightStart).toLowerCase(Locale.ROOT);
        if (!gap.matches(".*\\p{L}.*")) return true; // только пробелы/знаки — ок
        for (String w : gap.split("[^\\p{L}]+")) {
            if (!w.isEmpty() && GAP_STOPWORDS.contains(w)) return false;
        }
        return true;
    }

    /* ================== Словари/нормализация ================== */

    private final Map<String, String> TOKEN_TO_ISO = new HashMap<>();

    private void initTokenMap() {
        // базовые ISO + ex-USSR + RON (для «лей Румынии»)
        for (String c : Arrays.asList(
                "USD","EUR","GBP","CHF","JPY","CNY","PLN","GEL","KZT","NOK","SEK","DKK","CZK","ISK",
                "BYN","UAH","AMD","AZN","KGS","TJS","TMT","UZS","MDL","RON"
        )) TOKEN_TO_ISO.put(c.toLowerCase(Locale.ROOT), c);

        // символы
        TOKEN_TO_ISO.put("$","USD"); TOKEN_TO_ISO.put("€","EUR"); TOKEN_TO_ISO.put("£","GBP");
        TOKEN_TO_ISO.put("¥","JPY"); TOKEN_TO_ISO.put("₸","KZT"); TOKEN_TO_ISO.put("₺","TRY");
        TOKEN_TO_ISO.put("₴","UAH"); TOKEN_TO_ISO.put("₽","RUB"); // RUB потом отфильтруем

        // русский/жаргон/EN
        map("доллар","USD","бакс","баксы","зелень","usd","usdt");
        map("евро","EUR","eur");
        map("фунт","GBP","стерлингов","gbp","pound","pounds");
        map("франк","CHF","chf");
        map("иена","JPY","йена","jpy","yen");
        map("юань","CNY","cny","yuan");
        map("злотый","PLN","pln","zloty","zloties");
        map("лари","GEL","gel","lari");
        map("тенге","KZT","kzt","tenge");

        // ex-USSR:
        map("белруб","BYN","br","byn","бел.руб");
        map("гривна","UAH","гривен","гривны","uah","hryvnia","hryvnias");
        map("драм","AMD","amd","dram","drams");
        map("сом","KGS","kgs","som","soms");          // киргизский
        map("сомони","TJS","tjs","somoni");           // таджикский
        map("сум","UZS","uzs","sum","soums");         // узбекский

        // рубль — специальный случай (RUB отфильтруем, BYN дадим через эвристику)
        map("рубль","RUB","руб","рублей","рубля","ruble","rub");
    }

    private void map(String base, String iso, String... aliases) {
        TOKEN_TO_ISO.put(base.toLowerCase(Locale.ROOT), iso);
        for (String a : aliases) TOKEN_TO_ISO.put(a.toLowerCase(Locale.ROOT), iso);
    }

    private static boolean isRub(String iso) { return "RUB".equalsIgnoreCase(iso); }

    private String normalizeCurrency(String tokenOrPhrase) {
        if (tokenOrPhrase == null) return null;
        String t = tokenOrPhrase.toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("[\\s\\u00A0]+", " ")
                .trim();

        // прямой словарь
        String direct = TOKEN_TO_ISO.get(t);
        if (direct != null) return direct;

        // неоднозначности по прилагательному/стране (крона/манат/лей/бел.рубль)
        String amb = resolveAmbiguousByAdjOrCountry(t);
        if (amb != null) return amb;

        // морфология RU (долларов, белорусских рублей, шведская крона...)
        String ruIso = ruMorph.toIso(t);
        if (ruIso != null) return ruIso;

        // ISO-3
        if (t.matches("^[a-z]{3}$")) return t.toUpperCase(Locale.ROOT);

        // англ. множественное
        if (t.matches("^[a-z]+s$")) {
            String sing = t.substring(0, t.length() - 1);
            String iso = TOKEN_TO_ISO.get(sing);
            if (iso != null) return iso;
        }
        return null;
    }

    private static String resolveAmbiguousByAdjOrCountry(String t) {
        // крона
        if (t.contains("крон")) {
            String iso = resolveKronaPhrase(t);
            if (iso != null) return iso;
        }
        // манат: Азербайджан (AZN) / Туркменистан (TMT)
        if (t.contains("манат")) {
            if (t.contains("азербайджан")) return "AZN";
            if (t.contains("туркмен"))     return "TMT";
            if (t.matches(".*\\bазербайджан(а|у|е|ом|ия|ии|ию|ией)\\b.*")) return "AZN";
            if (t.matches(".*\\bтуркменистан(а|у|е|ом)?\\b.*"))            return "TMT";
            if (t.matches(".*\\bтуркмени(я|и|ю|ей)\\b.*"))                 return "TMT";
        }
        // лей: Молдова (MDL) / Румыния (RON)
        if (t.contains("лей") || t.contains("лея") || t.contains("леев") || t.contains("леи") || t.contains("леу")) {
            if (t.contains("молдав") || t.contains("молдова")) return "MDL";
            if (t.contains("румын"))                            return "RON";
            if (t.matches(".*\\bмолдов(а|ы|е|у|ой)\\b.*"))      return "MDL";
            if (t.matches(".*\\bрумын(ия|ии|ию|ией)\\b.*"))     return "RON";
        }
        // рубль: BYN / RUB
        if (t.contains("руб")) {
            if (t.contains("белорус")) return "BYN";
            if (t.contains("российск")) return "RUB";
            if (t.matches(".*\\bбеларус(ь|и|ью)\\b.*") || t.matches(".*\\bбелорусси(я|и|ю|ей)\\b.*")) return "BYN";
            if (t.matches(".*\\bросси(я|и|ю|ей)\\b.*")) return "RUB";
        }
        return null;
    }

    private static String resolveKronaPhrase(String t) {
        if (!t.contains("крон")) return null;
        if (t.contains("норвеж")) return "NOK";
        if (t.contains("швед"))   return "SEK";
        if (t.contains("датск"))  return "DKK";
        if (t.contains("чеш"))    return "CZK";
        if (t.contains("исланд")) return "ISK";
        // страны
        if (t.matches(".*\\bнорвег(ия|ии|ию|ией)\\b.*")) return "NOK";
        if (t.matches(".*\\bшвеци(я|и|ю|ей)\\b.*"))     return "SEK";
        if (t.matches(".*\\bдани(я|и|ю|ей)\\b.*"))      return "DKK";
        if (t.matches(".*\\bчехи(я|и|ю|ей)\\b.*"))      return "CZK";
        if (t.matches(".*\\bисланди(я|и|ю|ей)\\b.*"))   return "ISK";
        return null;
    }

    /* ================== Основная логика ================== */

    @Override
    public boolean needHandle(TelegramMessage message) {
        List<String> candidates = collectCandidateTexts(message);
        for (String candidate: candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            Matcher m = P.matcher(candidate);
            while (m.find()) {
                String curToken = firstNonNull(m.group("cur1"), m.group("cur2"));
                if (curToken == null) continue;
                String iso = normalizeCurrency(curToken);
                if (iso != null && !isRub(iso)) return true;
            }
        }
        return false;
    }

    @Override
    public void handle(TelegramMessage entity) {
        List<String> candidates = collectCandidateTexts(entity);
        List<FoundMoney> found = new ArrayList<>();

        for (String src : candidates) {
            if (src == null || src.isBlank()) continue;
            found.addAll(extractMentions(src));
        }
        // отбрасываем RUB (российские рубли)
        found.removeIf(f -> isRub(f.iso));

        if (found.isEmpty()) return;

        DecimalFormat df = new DecimalFormat("#,##0.00");
        List<String> lines = new ArrayList<>();

        for (FoundMoney fm : found) {
            try {
                BigDecimal rate = rateClient.rateToRub(fm.iso); // RUB за 1 единицу
                if (rate == null) continue;
                BigDecimal rub = fm.amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                lines.add(df.format(fm.amount) + " " + fm.iso + " ≈ " +
                        df.format(rub) + " RUB (курс " + df.format(rate) + ")");
            } catch (Exception ignore) {}
        }

        if (!lines.isEmpty()) {
            sendAnswer(entity, String.join("\n", dedup(lines)));
        }
    }

    private List<String> dedup(List<String> lines) {
        return new ArrayList<>(new LinkedHashSet<>(lines));
    }

    private List<FoundMoney> extractMentions(String text) {
        Matcher m = P.matcher(text);
        List<FoundMoney> out = new ArrayList<>();
        while (m.find()) {
            String curToken = firstNonNull(m.group("cur1"), m.group("cur2"));
            String amtToken = firstNonNull(m.group("amount1"), m.group("amount2"));
            if (curToken == null || amtToken == null) continue;

            // проверка «разрыва» между числом и валютой
            String gAmt = firstNonNullGroupName(m, "amount1", "amount2");
            String gCur = firstNonNullGroupName(m, "cur1", "cur2");
            int amountStart = m.start(gAmt), amountEnd = m.end(gAmt);
            int curStart = m.start(gCur),   curEnd   = m.end(gCur);
            int leftEnd = Math.min(amountEnd, curEnd);
            int rightStart = Math.max(amountStart, curStart);
            if (!gapIsSafe(text, leftEnd, rightStart)) continue;

            String iso = normalizeCurrency(curToken);
            if (iso == null || isRub(iso)) continue;

            BigDecimal amount = parseAmount(amtToken);
            if (amount == null) continue;

            // множители: суффикс и через пробел
            String multSuf = firstNonNull(m.group("mult1suf"), m.group("mult2suf"));
            String multSep = firstNonNull(m.group("mult1"),    m.group("mult2"));

            if (multSuf != null) amount = applyMultiplier(amount, multSuf);
            if (multSep != null) amount = applyMultiplier(amount, multSep);

            out.add(new FoundMoney(amount, iso));
        }
        return out;
    }

    private static String firstNonNull(String a, String b) { return a != null ? a : b; }
    private static String firstNonNullGroupName(Matcher m, String a, String b) { return m.group(a) != null ? a : b; }

    private static BigDecimal parseAmount(String s) {
        if (s == null) return null;
        String norm = s.replace("\u00A0", " ").replace(" ", "").replace(",", ".");
        try { return new BigDecimal(norm); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal applyMultiplier(BigDecimal base, String multRaw) {
        String mult = multRaw.toLowerCase(Locale.ROOT).replace(".", "").trim();

        // k/kk (латиница) и к/кк (кириллица)
        if (mult.equals("kk") || mult.equals("кк")) return base.multiply(BigDecimal.valueOf(1_000_000L));
        if (mult.equals("k")  || mult.equals("к")  || mult.equals("тыс") || mult.startsWith("тысяч"))
            return base.multiply(BigDecimal.valueOf(1_000L));

        // млн/миллион(ов)
        if (mult.equals("млн") || mult.startsWith("миллион"))
            return base.multiply(BigDecimal.valueOf(1_000_000L));

        // млрд/миллиард(ов)
        if (mult.equals("млрд") || mult.startsWith("миллиард"))
            return base.multiply(BigDecimal.valueOf(1_000_000_000L));

        return base;
    }

    private List<String> collectCandidateTexts(TelegramMessage e) {
        return Arrays.asList(
                safe(() -> e.getText()),
                safe(() -> e.getCaption()),
                safe(() -> e.getReply())
        );
    }

    private interface SupplierEx<T> { T get() throws Exception; }
    private static <T> T safe(SupplierEx<T> s) {
        try { return s.get(); } catch (Throwable ignore) { return null; }
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

    /* ================== Вспомогательные структуры ================== */

    private static final class FoundMoney {
        final BigDecimal amount; final String iso;
        FoundMoney(BigDecimal amount, String iso) { this.amount = amount; this.iso = iso; }
    }
}
