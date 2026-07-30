package com.fantasy.lnb.utils;

import java.util.Arrays;
import java.util.List;

public class ProfanityFilter {

    private static final List<String> BANNED_WORDS = Arrays.asList(
            "puto", "puta", "mierda", "concha", "verga", "pito", "choto", "culo", "forro", "pelotudo", "boludo",
            "trola", "trolo", "pija", "pete", "gato", "caca", "pedo", "putazo", "putito", "putita"
    );

    public static boolean containsProfanity(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        String lowerCaseText = text.toLowerCase().trim();
        
        for (String word : BANNED_WORDS) {
            // Simple check: if the text contains the word as a substring.
            // For a more robust check, we could use regex to check for whole words only.
            // e.g. text.matches(".*\\b" + word + "\\b.*")
            if (lowerCaseText.matches(".*\\b" + word + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    public static void validate(String... texts) {
        for (String text : texts) {
            if (containsProfanity(text)) {
                throw new IllegalArgumentException("El nombre contiene palabras no permitidas. Por favor, elegí otro.");
            }
        }
    }
}
