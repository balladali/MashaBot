package ru.balladali.balladalibot.balladalibot.core.entity;

public enum Language {
    /**
     * The afrikaans.
     */
    AFRIKAANS("af"),

    /**
     * The albanian.
     */
    ALBANIAN("sq"),

    /**
     * The amharic.
     */
    AMHARIC("am"),

    /**
     * The arabic.
     */
    ARABIC("ar"),

    /**
     * The armenian.
     */
    ARMENIAN("hy"),

    /**
     * The azerbaijani.
     */
    AZERBAIJANI("az"),

    /**
     * The bashkir.
     */
    BASHKIR("ba"),

    /**
     * The basque.
     */
    BASQUE("eu"),

    /**
     * The belarusian.
     */
    BELARUSIAN("be"),

    /**
     * The bengali.
     */
    BENGALI("bn"),

    /**
     * The bosnian.
     */
    BOSNIAN("bs"),

    /**
     * The bulgarian.
     */
    BULGARIAN("bg"),

    /**
     * The burmese.
     */
    BURMESE("my"),

    /**
     * The catalan.
     */
    CATALAN("ca"),

    /**
     * The cebuano.
     */
    CEBUANO("ceb"),

    /**
     * The chinese.
     */
    CHINESE("zh"),

    /**
     * The croatian.
     */
    CROATIAN("hr"),

    /**
     * The czech.
     */
    CZECH("cs"),

    /**
     * The danish.
     */
    DANISH("da"),

    /**
     * The dutch.
     */
    DUTCH("nl"),

    /**
     * The emoji.
     */
    EMOJI("emj"),

    /**
     * The english.
     */
    ENGLISH("en"),

    /**
     * The esperanto.
     */
    ESPERANTO("eo"),

    /**
     * The estonian.
     */
    ESTONIAN("et"),

    /**
     * The finnish.
     */
    FINNISH("fi"),

    /**
     * The french.
     */
    FRENCH("fr"),

    /**
     * The galician.
     */
    GALICIAN("gl"),

    /**
     * The georgian.
     */
    GEORGIAN("ka"),

    /**
     * The german.
     */
    GERMAN("de"),

    /**
     * The greek.
     */
    GREEK("el"),

    /**
     * The gujarati.
     */
    GUJARATI("gu"),

    /**
     * The haitian.
     */
    HAITIAN("ht"),

    /**
     * The hebrew.
     */
    HEBREW("he"),

    /**
     * The hill mari.
     */
    HILL_MARI("mrj"),

    /**
     * The hindi.
     */
    HINDI("hi"),

    /**
     * The hungarian.
     */
    HUNGARIAN("hu"),

    /**
     * The icelandic.
     */
    ICELANDIC("is"),

    /**
     * The indonesian.
     */
    INDONESIAN("id"),

    /**
     * The irish.
     */
    IRISH("ga"),

    /**
     * The italian.
     */
    ITALIAN("it"),

    /**
     * The japanese.
     */
    JAPANESE("ja"),

    /**
     * The javanese.
     */
    JAVANESE("jv"),

    /**
     * The kannada.
     */
    KANNADA("kn"),

    /**
     * The kazakh.
     */
    KAZAKH("kk"),

    /**
     * The khmer.
     */
    KHMER("km"),

    /**
     * The korean.
     */
    KOREAN("ko"),

    /**
     * The kyrgyz.
     */
    KYRGYZ("ky"),

    /**
     * The lao.
     */
    LAO("lo"),

    /**
     * The latin.
     */
    LATIN("la"),

    /**
     * The latvian.
     */
    LATVIAN("lv"),

    /**
     * The lithuanian.
     */
    LITHUANIAN("lt"),

    /**
     * The luxembourgish.
     */
    LUXEMBOURGISH("lb"),

    /**
     * The macedonian.
     */
    MACEDONIAN("mk"),

    /**
     * The malagasy.
     */
    MALAGASY("mg"),

    /**
     * The malay.
     */
    MALAY("ms"),

    /**
     * The malayalam.
     */
    MALAYALAM("ml"),

    /**
     * The maltese.
     */
    MALTESE("mt"),

    /**
     * The maori.
     */
    MAORI("mi"),

    /**
     * The marathi.
     */
    MARATHI("mr"),

    /**
     * The mari.
     */
    MARI("mhr"),

    /**
     * The mongolian.
     */
    MONGOLIAN("mn"),

    /**
     * The nepali.
     */
    NEPALI("ne"),

    /**
     * The norwegian.
     */
    NORWEGIAN("no"),

    /**
     * The papiamento.
     */
    PAPIAMENTO("pap"),

    /**
     * The persian.
     */
    PERSIAN("fa"),

    /**
     * The polish.
     */
    POLISH("pl"),

    /**
     * The portuguese.
     */
    PORTUGUESE("pt"),

    /**
     * The punjabi.
     */
    PUNJABI("pa"),

    /**
     * The romanian.
     */
    ROMANIAN("ro"),

    /**
     * The russian.
     */
    RUSSIAN("ru"),

    /**
     * The scottish gaelic.
     */
    SCOTTISH_GAELIC("gd"),

    /**
     * The serbian.
     */
    SERBIAN("sr"),

    /**
     * The sinhalese.
     */
    SINHALESE("si"),

    /**
     * The slovak.
     */
    SLOVAK("sk"),

    /**
     * The slovenian.
     */
    SLOVENIAN("sl"),

    /**
     * The spanish.
     */
    SPANISH("es"),

    /**
     * The sundanese.
     */
    SUNDANESE("su"),

    /**
     * The swahili.
     */
    SWAHILI("sw"),

    /**
     * The swedish.
     */
    SWEDISH("sv"),

    /**
     * The tagalog.
     */
    TAGALOG("tl"),

    /**
     * The tajik.
     */
    TAJIK("tg"),

    /**
     * The tamil.
     */
    TAMIL("ta"),

    /**
     * The tatar.
     */
    TATAR("tt"),

    /**
     * The telugu.
     */
    TELUGU("te"),

    /**
     * The thai.
     */
    THAI("th"),

    /**
     * The turkish.
     */
    TURKISH("tr"),

    /**
     * The udmurt.
     */
    UDMURT("udm"),

    /**
     * The ukrainian.
     */
    UKRAINIAN("uk"),

    /**
     * The urdu.
     */
    URDU("ur"),

    /**
     * The uzbek.
     */
    UZBEK("uz"),

    /**
     * The vietnamese.
     */
    VIETNAMESE("vi"),

    /**
     * The welsh.
     */
    WELSH("cy"),

    /**
     * The xhosa.
     */
    XHOSA("xh"),

    /**
     * The yiddish.
     */
    YIDDISH("yi");

    private String language;

    Language(String language) {
        this.language = language;
    }

    @Override
    public String toString() {
        return this.language;
    }

    public static Language fromString(final String pLanguage) {
        for (Language l : values()) {
            if (l.toString().equals(pLanguage)) {
                return l;
            }
        }
        return null;
    }
}
