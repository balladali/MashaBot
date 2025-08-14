package ru.balladali.mashabot.core.services;

import com.github.demidko.aot.WordformMeaning;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.github.demidko.aot.WordformMeaning.lookupForMeanings;

/** Лемматизация RU валютных фраз → ISO на AOT (без PartOfSpeech, по строковым тегам морфологии). */
public class CurrencyMorphNormalizerAot {

    private final Map<String, String> lemmaToIso;
    private final Map<String, String> adjKronaToIso;

    private final ConcurrentHashMap<String, LemmaMorph> cache = new ConcurrentHashMap<>();

    public CurrencyMorphNormalizerAot(Map<String, String> lemmaToIso,
                                      Map<String, String> adjKronaToIso) {
        this.lemmaToIso = Objects.requireNonNull(lemmaToIso);
        this.adjKronaToIso = Objects.requireNonNull(adjKronaToIso);
    }

    public String toIso(String phrase) {
        if (phrase == null || phrase.isBlank()) return null;

        String cleaned = phrase.toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("[\\s\\u00A0]+", " ")
                .trim();

        String[] tokens = cleaned.split(" ");
        if (tokens.length == 0) return null;

        List<Token> parsed = Arrays.stream(tokens)
                .map(t -> {
                    LemmaMorph lm = cache.computeIfAbsent(t, CurrencyMorphNormalizerAot::lookupLemmaMorph);
                    return new Token(t, lm.lemma, lm.morph);
                })
                .collect(Collectors.toList());

        List<Token> nouns = parsed.stream().filter(Token::isNoun).collect(Collectors.toList());
        List<Token> adjs  = parsed.stream().filter(Token::isAdj).collect(Collectors.toList());

        // "крона" + прилагательное-страна → ISO
        Optional<Token> krona = nouns.stream().filter(t -> "крона".equals(t.lemma)).findFirst();
        if (krona.isPresent() && !adjs.isEmpty()) {
            String adjLemma = stripRusAdjSuffix(adjs.get(0).lemma); // "норвежский" -> "норвежск"
            String iso = adjKronaToIso.get(adjLemma);
            if (iso != null) return iso;
        }

        // одиночное существительное по лемме
        for (Token n : nouns) {
            String iso = lemmaToIso.get(n.lemma);
            if (iso != null) return iso;
        }

        // fallback: среди всех лемм (для жаргона)
        for (Token t : parsed) {
            String iso = lemmaToIso.get(t.lemma);
            if (iso != null) return iso;
        }

        return null;
    }

    /* ===================== helpers ===================== */

    private static final class LemmaMorph {
        final String lemma;
        final String morph; // строковые теги, например "[С, мр, им, мн]"
        LemmaMorph(String lemma, String morph) { this.lemma = lemma; this.morph = morph; }
    }

    private static LemmaMorph lookupLemmaMorph(String word) {
        try {
            List<WordformMeaning> ms = lookupForMeanings(word);
            if (ms.isEmpty()) return new LemmaMorph(word, "");
            WordformMeaning m = ms.get(0);
            String lemma = safeLower(m.getLemma().toString(), word);
            String morph = String.valueOf(m.getMorphology()); // чаще "[С, ...]" или "[П, ...]"
            return new LemmaMorph(lemma, morph);
        } catch (Exception e) {
            return new LemmaMorph(word, "");
        }
    }

    private static String safeLower(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s.toLowerCase(Locale.ROOT) : fallback;
    }

    private static String stripRusAdjSuffix(String lemma) {
        return lemma.replaceAll("(ий|ый|ая|ое|ие|их|ым|ом|ой|ую)$", "");
    }

    private static final class Token {
        final String raw;
        final String lemma;
        final String morph;

        Token(String raw, String lemma, String morph) {
            this.raw = raw;
            this.lemma = lemma;
            this.morph = morph != null ? morph : "";
        }

        boolean isNoun() { return morph.startsWith("[С"); }   // "[С, мр, ...]"
        boolean isAdj()  { return morph.startsWith("[П"); }   // "[П, жр, ...]"
    }

    /** Фабрика с дефолтными словарями. */
    public static CurrencyMorphNormalizerAot defaultRu() {
        Map<String,String> lemmaToIso = new HashMap<>();
        lemmaToIso.put("доллар", "USD");
        lemmaToIso.put("евро",   "EUR");
        lemmaToIso.put("фунт",   "GBP");
        lemmaToIso.put("франк",  "CHF");
        lemmaToIso.put("лира",   "TRY");
        lemmaToIso.put("крона",  null); // неоднозначно без прилагательного
        lemmaToIso.put("иена",   "JPY");
        lemmaToIso.put("йена",   "JPY");
        lemmaToIso.put("юань",   "CNY");
        lemmaToIso.put("тенге",  "KZT");
        lemmaToIso.put("злотый", "PLN");
        lemmaToIso.put("лари",   "GEL");
        lemmaToIso.put("рубль",  "RUB");
        // жаргон
        lemmaToIso.put("бакс",   "USD");
        lemmaToIso.put("зелень", "USD");

        Map<String,String> adjKronaToIso = new HashMap<>();
        adjKronaToIso.put("норвежск", "NOK");
        adjKronaToIso.put("шведск",   "SEK");
        adjKronaToIso.put("датск",    "DKK");
        adjKronaToIso.put("чешск",    "CZK");
        adjKronaToIso.put("исландск", "ISK");

        return new CurrencyMorphNormalizerAot(lemmaToIso, adjKronaToIso);
    }
}
