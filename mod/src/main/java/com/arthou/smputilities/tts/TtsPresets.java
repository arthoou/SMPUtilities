package com.arthou.smputilities.tts;

import com.arthou.smputilities.mc.Texts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TtsPresets {
    public static final int MODE_FEMININE_1 = 1;
    public static final int MODE_FEMININE_2 = 2;
    public static final int MODE_FEMININE_3 = 3;
    public static final int MODE_MASCULINE_1 = 4;
    public static final int MODE_MASCULINE_2 = 5;
    public static final int MODE_DUCK_FEMININE = 6;
    public static final int MODE_DUCK_MASCULINE = 7;
    public static final int MAX_MODE = MODE_DUCK_MASCULINE;

    private static final String ENGLISH_IVY = "Amazon US English (Ivy)";
    private static final String ENGLISH_SALLI = "Amazon US English (Salli)";
    private static final String ENGLISH_KIMBERLY = "Amazon US English (Kimberly)";
    private static final String ENGLISH_JOANNA = "Amazon US English (Joanna)";
    private static final String ENGLISH_JOEY = "Amazon US English (Joey)";
    private static final String ENGLISH_BRIAN = "Amazon British English (Brian)";
    private static final String SPANISH_CONCHITA = "Amazon Castilian Spanish (Conchita)";
    private static final String SPANISH_LUCIA = "Amazon Castilian Spanish (Lucia)";
    private static final String SPANISH_ENRIQUE = "Amazon Castilian Spanish (Enrique)";
    private static final String SPANISH_PABLO = "Microsoft Spanish (Pablo)";
    private static final String PORTUGUESE_VITORIA = "Amazon Brazilian Portuguese (Vitoria)";
    private static final String PORTUGUESE_LETICIA = "RHVoice Brazilian Portuguese (Letícia-F123)";
    private static final String PORTUGUESE_RICARDO = "Amazon Brazilian Portuguese (Ricardo)";
    private static final String PORTUGUESE_DANIEL = "Microsoft Brazilian Portuguese (Daniel)";
    private static final String POLISH_PAULINA = "Microsoft Polish (Paulina)";
    private static final String POLISH_EWA = "Amazon Polish (Ewa)";
    private static final String POLISH_JACEK = "Amazon Polish (Jacek)";
    private static final String POLISH_JAN = "Amazon Polish (Jan)";
    private static final String FRENCH_CELINE = "Amazon French (Celine)";
    private static final String FRENCH_MATHIEU = "Amazon French (Mathieu)";
    private static final String GERMAN_MARLENE = "Amazon German (Marlene)";
    private static final String GERMAN_HANS = "Amazon German (Hans)";
    private static final String ITALIAN_CARLA = "Amazon Italian (Carla)";
    private static final String ITALIAN_GIORGIO = "Amazon Italian (Giorgio)";
    private static final String DUTCH_LOTTE = "Amazon Dutch (Lotte)";
    private static final String DUTCH_RUBEN = "Amazon Dutch (Ruben)";
    private static final Pattern POLISH_CHARS = Pattern.compile("[ąćęłńóśźż]");
    private static final Pattern PORTUGUESE_CHARS = Pattern.compile("[ãõç]");
    private static final Pattern SPANISH_CHARS = Pattern.compile("[ñ¿¡]");
    private static final Pattern FRENCH_CHARS = Pattern.compile("[àâæçéèêëîïôœùûüÿ]");
    private static final Pattern GERMAN_CHARS = Pattern.compile("[äöüß]");
    private static final Pattern ITALIAN_CHARS = Pattern.compile("[àèéìíîòóùú]");
    private static final List<String> SUGGESTION_LANGUAGES = List.of(
        "english", "spanish", "portuguese", "polish", "french", "german", "italian", "dutch"
    );

    private TtsPresets() {
    }

    public static String resolveVoiceName(VoiceLanguage language, int mode) {
        int normalizedMode = normalizeMode(mode);
        return switch (normalizedMode) {
            case MODE_FEMININE_2 -> resolveFeminineTwo(language);
            case MODE_FEMININE_3 -> resolveFeminineThree(language);
            case MODE_MASCULINE_1 -> resolveMasculineOne(language);
            case MODE_MASCULINE_2 -> resolveMasculineTwo(language);
            case MODE_DUCK_FEMININE -> resolveDuckFeminine(language);
            case MODE_DUCK_MASCULINE -> resolveDuckMasculine(language);
            default -> resolveFeminineOne(language);
        };
    }

    public static String resolvePitch(int mode) {
        int normalizedMode = normalizeMode(mode);
        return normalizedMode == MODE_DUCK_FEMININE || normalizedMode == MODE_DUCK_MASCULINE ? "high" : "medium";
    }

    public static List<String> suggestionLanguages() {
        return SUGGESTION_LANGUAGES;
    }

    public static List<String> suggestionVariants(int maxVariant) {
        return maxVariant >= 3 ? List.of("1", "2", "3") : List.of("1", "2");
    }

    public static int modeFromSelection(String gender, int variant) {
        if (gender == null) {
            return -1;
        }

        return switch (gender.trim().toLowerCase(Locale.ROOT)) {
            case "feminine", "feminina", "female", "f" -> switch (variant) {
                case 1 -> MODE_FEMININE_1;
                case 2 -> MODE_FEMININE_2;
                case 3 -> MODE_FEMININE_3;
                default -> -1;
            };
            case "masculine", "masculina", "masculino", "male", "m" -> switch (variant) {
                case 1 -> MODE_MASCULINE_1;
                case 2 -> MODE_MASCULINE_2;
                default -> -1;
            };
            default -> -1;
        };
    }

    public static VoiceLanguage languageFromInput(String input) {
        if (input == null) {
            return null;
        }

        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "english", "en", "ingles", "inglês" -> VoiceLanguage.ENGLISH;
            case "spanish", "es", "espanol", "español", "espanhol" -> VoiceLanguage.SPANISH;
            case "portuguese", "pt", "ptbr", "pt-br", "pt_br", "portugues", "português", "br" -> VoiceLanguage.PORTUGUESE;
            case "polish", "pl", "polishj", "polones", "polonês", "polski" -> VoiceLanguage.POLISH;
            case "french", "fr", "frances", "francês", "francais", "français" -> VoiceLanguage.FRENCH;
            case "german", "de", "alemao", "alemão", "deutsch" -> VoiceLanguage.GERMAN;
            case "italian", "it", "italiano", "italiana" -> VoiceLanguage.ITALIAN;
            case "dutch", "nl", "holandes", "holandês", "nederlands" -> VoiceLanguage.DUTCH;
            default -> null;
        };
    }

    public static String selectionName(int mode, VoiceLanguage language) {
        int normalizedMode = normalizeMode(mode);
        if (normalizedMode == MODE_DUCK_FEMININE || normalizedMode == MODE_DUCK_MASCULINE) {
            String gender = normalizedMode == MODE_DUCK_MASCULINE ? "masculine" : "feminine";
            String languageName = (language == null ? VoiceLanguage.ENGLISH : language).name().toLowerCase(Locale.ROOT);
            return "duck " + gender + " " + languageName;
        }

        String gender = switch (normalizedMode) {
            case MODE_MASCULINE_1, MODE_MASCULINE_2 -> "masculine";
            default -> "feminine";
        };
        int variant = switch (normalizedMode) {
            case MODE_FEMININE_1, MODE_MASCULINE_1 -> 1;
            case MODE_FEMININE_2, MODE_MASCULINE_2 -> 2;
            case MODE_FEMININE_3 -> 3;
            default -> 1;
        };
        String languageName = (language == null ? VoiceLanguage.ENGLISH : language).name().toLowerCase(Locale.ROOT);
        return gender + " " + variant + " " + languageName;
    }

    /** Translatable language name, resolved in each player's own game language. */
    public static MutableComponent languageComponent(VoiceLanguage language) {
        VoiceLanguage safe = language == null ? VoiceLanguage.ENGLISH : language;
        return Texts.translatable("tts.language." + safe.name().toLowerCase(Locale.ROOT));
    }

    /** Like {@link #selectionName} but translated on the client: "Feminina 1 · Português". */
    public static MutableComponent selectionComponent(int mode, VoiceLanguage language) {
        int normalizedMode = normalizeMode(mode);
        boolean duck = normalizedMode == MODE_DUCK_FEMININE || normalizedMode == MODE_DUCK_MASCULINE;
        boolean masculine = switch (normalizedMode) {
            case MODE_MASCULINE_1, MODE_MASCULINE_2, MODE_DUCK_MASCULINE -> true;
            default -> false;
        };
        MutableComponent text = Texts.translatable(masculine ? "tts.gender.masculine" : "tts.gender.feminine");
        if (duck) {
            text = Texts.translatable("tts.duck", text);
        } else {
            int variant = switch (normalizedMode) {
                case MODE_FEMININE_2, MODE_MASCULINE_2 -> 2;
                case MODE_FEMININE_3 -> 3;
                default -> 1;
            };
            text.append(" " + variant);
        }
        return text.append(" · ").append(languageComponent(language));
    }

    private static int normalizeMode(int mode) {
        if (mode <= 0) {
            return 0;
        }

        if (mode <= MODE_DUCK_MASCULINE) {
            return mode;
        }

        return MODE_FEMININE_1;
    }

    private static String resolveFeminineOne(VoiceLanguage language) {
        return resolveByLanguage(language, ENGLISH_IVY, SPANISH_CONCHITA, PORTUGUESE_VITORIA, POLISH_PAULINA, FRENCH_CELINE, GERMAN_MARLENE, ITALIAN_CARLA, DUTCH_LOTTE);
    }

    private static String resolveFeminineTwo(VoiceLanguage language) {
        return resolveByLanguage(language, ENGLISH_SALLI, SPANISH_LUCIA, PORTUGUESE_VITORIA, POLISH_PAULINA, FRENCH_CELINE, GERMAN_MARLENE, ITALIAN_CARLA, DUTCH_LOTTE);
    }

    private static String resolveFeminineThree(VoiceLanguage language) {
        return resolveByLanguage(language, ENGLISH_KIMBERLY, SPANISH_CONCHITA, PORTUGUESE_LETICIA, POLISH_PAULINA, FRENCH_CELINE, GERMAN_MARLENE, ITALIAN_CARLA, DUTCH_LOTTE);
    }

    private static String resolveMasculineOne(VoiceLanguage language) {
        return resolveByLanguage(language, ENGLISH_JOEY, SPANISH_ENRIQUE, PORTUGUESE_RICARDO, POLISH_JACEK, FRENCH_MATHIEU, GERMAN_HANS, ITALIAN_GIORGIO, DUTCH_RUBEN);
    }

    private static String resolveMasculineTwo(VoiceLanguage language) {
        return resolveByLanguage(language, ENGLISH_BRIAN, SPANISH_PABLO, PORTUGUESE_DANIEL, POLISH_JAN, FRENCH_MATHIEU, GERMAN_HANS, ITALIAN_GIORGIO, DUTCH_RUBEN);
    }

    private static String resolveDuckFeminine(VoiceLanguage language) {
        return resolveByLanguage(language, ENGLISH_JOANNA, SPANISH_CONCHITA, PORTUGUESE_VITORIA, POLISH_EWA, FRENCH_CELINE, GERMAN_MARLENE, ITALIAN_CARLA, DUTCH_LOTTE);
    }

    private static String resolveDuckMasculine(VoiceLanguage language) {
        return resolveByLanguage(language, ENGLISH_JOEY, SPANISH_ENRIQUE, PORTUGUESE_RICARDO, POLISH_JACEK, FRENCH_MATHIEU, GERMAN_HANS, ITALIAN_GIORGIO, DUTCH_RUBEN);
    }

    private static String resolveByLanguage(
        VoiceLanguage language,
        String englishVoice,
        String spanishVoice,
        String portugueseVoice,
        String polishVoice,
        String frenchVoice,
        String germanVoice,
        String italianVoice,
        String dutchVoice
    ) {
        return switch (language) {
            case SPANISH -> spanishVoice;
            case PORTUGUESE -> portugueseVoice;
            case POLISH -> polishVoice;
            case FRENCH -> frenchVoice;
            case GERMAN -> germanVoice;
            case ITALIAN -> italianVoice;
            case DUTCH -> dutchVoice;
            default -> englishVoice;
        };
    }

    public static VoiceLanguage detectLanguage(String text) {
        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            return VoiceLanguage.ENGLISH;
        }

        if (POLISH_CHARS.matcher(normalized).find()
            || containsAnyWord(normalized, " jestem ", " sie ", " się ", " czesc ", " cześć ", " prosze ", " proszę ", " dziekuje ", " dziękuję ", " dobrze ", " bardzo ", " niech ", " moj ", " mój ", " twoj ", " twój ", " jak ", " gdzie ", " czy ")) {
            return VoiceLanguage.POLISH;
        }

        if (PORTUGUESE_CHARS.matcher(normalized).find()
            || containsAnyWord(normalized, " voce ", " você ", " nao ", " não ", " pra ", " estou ", " beleza ", " obrigado ", " obrigada ", " que ", " oi ", " tudo ", " mesmo ", " cara ", " mano ", " gente ", " com ")) {
            return VoiceLanguage.PORTUGUESE;
        }

        if (SPANISH_CHARS.matcher(normalized).find()
            || containsAnyWord(normalized, " hola ", " gracias ", " por favor ", " como ", " cómo ", " estoy ", " eres ", " para ", " pero ", " que ", " qué ", " bueno ", " vale ", " donde ", " dónde ", " estoy ", " muy ")) {
            return VoiceLanguage.SPANISH;
        }

        if (FRENCH_CHARS.matcher(normalized).find()
            || containsAnyWord(normalized, " bonjour ", " merci ", " avec ", " pour ", " mais ", " tres ", " très ", " bien ", " suis ", " dans ")) {
            return VoiceLanguage.FRENCH;
        }

        if (GERMAN_CHARS.matcher(normalized).find()
            || containsAnyWord(normalized, " hallo ", " danke ", " bitte ", " ich ", " nicht ", " und ", " aber ", " sehr ", " gut ")) {
            return VoiceLanguage.GERMAN;
        }

        if (ITALIAN_CHARS.matcher(normalized).find()
            || containsAnyWord(normalized, " ciao ", " grazie ", " prego ", " sono ", " molto ", " bene ", " perche ", " perché ")) {
            return VoiceLanguage.ITALIAN;
        }

        if (containsAnyWord(normalized, " hoi ", " dank ", " bedankt ", " alsjeblieft ", " niet ", " goed ", " maar ", " ben ")) {
            return VoiceLanguage.DUTCH;
        }

        return VoiceLanguage.ENGLISH;
    }

    private static boolean containsAnyWord(String value, String... words) {
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeText(String text) {
        String safe = text == null ? "" : text;
        return (" " + safe.toLowerCase(Locale.ROOT) + " ").replace('\n', ' ').replace('\r', ' ');
    }

    public enum VoiceLanguage {
        ENGLISH,
        SPANISH,
        PORTUGUESE,
        POLISH,
        FRENCH,
        GERMAN,
        ITALIAN,
        DUTCH
    }
}
