package com.example;

import java.util.regex.Pattern;

/**
 * Normalizes speech by stripping wake phrases, removing noise,
 * and harmonizing Hindi/Hinglish/English variations into clean command strings.
 */
public class CommandNormalizer {

    private static final Pattern WAKE_PATTERNS = Pattern.compile(
            "^(hey\\s+zava|zava\\s+ji|zava\\s+please|hey\\s+java|ok\\s+zava|zava|हे\\s+ज़ावा|हे\\s+जावा|ज़ावा)[,\\s]*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    public static String normalize(String rawSpeech) {
        if (rawSpeech == null) return "";

        String text = rawSpeech.trim();

        // 1. Strip leading wake words
        text = WAKE_PATTERNS.matcher(text).replaceFirst("").trim();

        // 2. Remove trailing wake phrases if user said "YouTube kholo hey zava"
        text = text.replaceAll("(?i)[,\\s]+(hey\\s+zava|zava)$", "").trim();

        // 3. Remove extraneous polite preambles
        text = text.replaceAll("(?i)^(please|kripya|meherbaani\\s+karke|mujhe)\\s+", "").trim();

        // 4. Normalize common Hinglish action verbs for uniform pattern matching
        // e.g., "open kar do" -> "open karo"
        text = text.replaceAll("(?i)\\bopen\\s+kar\\s+do\\b", "open karo");
        text = text.replaceAll("(?i)\\bchalu\\s+kar\\s+do\\b", "chalu karo");
        text = text.replaceAll("(?i)\\bband\\s+kar\\s+do\\b", "band karo");

        // 5. Clean punctuation but retain essential characters
        text = text.replaceAll("[,.?!;:]+", " ").replaceAll("\\s+", " ").trim();

        return text;
    }
}
