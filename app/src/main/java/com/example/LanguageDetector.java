package com.example;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Detects whether a command is in Hindi (Devanagari), Hinglish (Romanized Hindi), or English.
 */
public class LanguageDetector {

    public enum Language {
        HINDI,
        HINGLISH,
        ENGLISH
    }

    private static final Set<String> HINGLISH_MARKERS = new HashSet<>(Arrays.asList(
            "kholo", "chalao", "dikhao", "karo", "band", "bhejo", "badhao", "kam",
            "ji", "boliye", "mujhe", "aur", "me", "jao", "par", "likha", "hai", "kya",
            "suno", "padho", "dekh", "kar", "do", "karke", "batao", "banao", "chalu",
            "lekin", "dobara", "thik", "haan", "nahi", "ruko"
    ));

    public static Language detect(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Language.ENGLISH;
        }

        // 1. Check for Devanagari script characters
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.DEVANAGARI) {
                return Language.HINDI;
            }
        }

        // 2. Check for Romanized Hindi (Hinglish) tokens
        String clean = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
        String[] tokens = clean.split("\\s+");
        for (String token : tokens) {
            if (HINGLISH_MARKERS.contains(token)) {
                return Language.HINGLISH;
            }
        }

        return Language.ENGLISH;
    }
}
