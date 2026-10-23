package ru.balladali.mashabot.core.services;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Only currency vocabulary: no full-language dictionary or growing input cache. */
public final class CurrencyNameNormalizer {
    private static final Map<String, String> FORMS;
    private static final String CURRENCY_PATTERN;

    static {
        Map<String, String> forms = new HashMap<>();
        masculine(forms, "USD", "доллар", "бакс");
        masculine(forms, "GBP", "фунт");
        masculine(forms, "CHF", "франк");
        masculine(forms, "AMD", "драм");
        masculine(forms, "KGS", "сом");
        masculine(forms, "UZS", "сум");
        feminine(forms, "TRY", "лир", "лир", "лирами", "лирах");
        feminine(forms, "UAH", "гривн", "гривен", "гривнами", "гривнах");
        feminine(forms, "JPY", "иен", "иен", "иенами", "иенах");
        feminine(forms, "JPY", "йен", "йен", "йенами", "йенах");
        add(forms, "CNY", "юань юаня юаню юанем юане юани юаней юаням юанями юанях");
        add(forms, "PLN", "злотый злотого злотому злотым злотом злотые злотых злотыми");
        add(forms, "EUR", "евро");
        add(forms, "GEL", "лари");
        add(forms, "KZT", "тенге");
        add(forms, "TJS", "сомони");
        add(forms, "USD", "зелень зелени зеленью");
        add(forms, "RUB", "рубль рубля рублю рублем рублём рубле рубли рублей рублям рублями рублях");
        // Ambiguous names are matched here, then resolved by country in the handler.
        feminine(forms, "", "крон", "крон", "кронами", "кронах");
        masculine(forms, "", "манат");
        add(forms, "", "лей лея лею леем лее леи леев леям леями леях леу");
        FORMS = Map.copyOf(forms);
        CURRENCY_PATTERN = forms.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .map(Pattern::quote).collect(Collectors.joining("|", "(?:", ")"));
    }

    private CurrencyNameNormalizer() {}

    private static void add(Map<String, String> forms, String iso, String words) {
        for (String word : words.split(" ")) forms.put(word, iso);
    }

    private static void masculine(Map<String, String> forms, String iso, String... stems) {
        for (String stem : stems) {
            for (String suffix : List.of("", "а", "у", "ом", "е", stem.endsWith("к") ? "и" : "ы", "ов", "ам", "ами", "ах")) {
                forms.put(stem + suffix, iso);
            }
        }
    }

    private static void feminine(Map<String, String> forms, String iso, String stem,
                                 String pluralGenitive, String pluralInstrumental, String pluralPrepositional) {
        for (String suffix : List.of("а", "ы", "е", "у", "ой", "ою", "ам")) forms.put(stem + suffix, iso);
        add(forms, iso, pluralGenitive + " " + pluralInstrumental + " " + pluralPrepositional);
    }

    public static String currencyPattern() { return CURRENCY_PATTERN; }

    public static String toIso(String phrase) {
        if (phrase == null || phrase.isBlank()) return null;
        String normalized = phrase.toLowerCase(Locale.ROOT).replace('-', ' ')
                .replaceAll("[\\s\\u00A0]+", " ").trim();
        String iso = FORMS.get(normalized.substring(normalized.lastIndexOf(' ') + 1));
        return iso == null || iso.isEmpty() ? null : iso;
    }
}
