package com.dimitriongoua.smsforwarder.destination;

import java.util.Collection;

/** Les secrets (valeurs d'en-tête, token du bot) ne doivent jamais apparaître en clair. */
public final class Secrets {
    public static final String MASK = "••••••";

    private Secrets() {
    }

    /** Remplace chaque secret présent dans le texte (log, message d'erreur) par « *** ». */
    public static String redact(String text, Collection<String> secrets) {
        if (text == null) return null;
        String result = text;
        for (String secret : secrets) {
            // Un secret trop court masquerait n'importe quel morceau de texte.
            if (secret != null && secret.length() >= 4) result = result.replace(secret, "***");
        }
        return result;
    }

    /** Affichage d'une valeur secrète : masquée, ou vide si aucune valeur. */
    public static String mask(String value) {
        return value == null || value.isEmpty() ? "" : MASK;
    }
}
