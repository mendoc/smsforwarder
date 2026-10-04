package com.dimitriongoua.smsforwarder.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Décide si un SMS est relayé :
 * <ol>
 *     <li>si au moins une règle d'exclusion correspond, le SMS n'est pas relayé ;</li>
 *     <li>sinon, il est relayé si son expéditeur est dans la liste des expéditeurs autorisés
 *     (comparaison sans tenir compte de la casse) ou si au moins une règle d'inclusion
 *     correspond.</li>
 * </ol>
 * Classe sans dépendance Android, testée par SmsFilterTest.
 */
public final class SmsFilter {

    /** Règle présente par défaut : reprend le comportement « contient paypal » de la v1.2.0. */
    public static final String DEFAULT_RULES = "body:(?i)paypal";

    private final List<String> allowedSenders;
    private final List<FilterRule> rules;

    public SmsFilter(List<String> allowedSenders, List<FilterRule> rules) {
        this.allowedSenders = new ArrayList<>(allowedSenders);
        this.rules = new ArrayList<>(rules);
    }

    /**
     * Construit le filtre à partir des règles enregistrées. Une règle devenue invalide est
     * ignorée : la réception d'un SMS ne doit jamais échouer à cause d'elle.
     */
    public static SmsFilter fromSettings(List<String> allowedSenders, List<String> ruleLines) {
        List<FilterRule> rules = new ArrayList<>();
        for (String line : ruleLines) {
            if (line == null || line.trim().isEmpty()) continue;
            try {
                rules.add(FilterRule.parse(line));
            } catch (InvalidRuleException ignored) {
                // Refusée à l'enregistrement : ne peut venir que d'une ancienne valeur.
            }
        }
        return new SmsFilter(allowedSenders, rules);
    }

    /**
     * Vérifie une liste de règles saisies, une par ligne (les lignes vides sont ignorées).
     *
     * @return les erreurs, vide si toutes les règles sont valides
     */
    public static List<InvalidRuleException> validate(List<String> ruleLines) {
        List<InvalidRuleException> errors = new ArrayList<>();
        for (String line : ruleLines) {
            if (line == null || line.trim().isEmpty()) continue;
            try {
                FilterRule.parse(line);
            } catch (InvalidRuleException e) {
                errors.add(e);
            }
        }
        return errors;
    }

    public boolean accepts(String from, String body) {
        for (FilterRule rule : rules) {
            if (rule.isExclusion() && rule.matches(from, body)) return false;
        }
        if (isAllowedSender(from)) return true;
        for (FilterRule rule : rules) {
            if (!rule.isExclusion() && rule.matches(from, body)) return true;
        }
        return false;
    }

    public boolean isAllowedSender(String from) {
        if (from == null) return false;
        String address = from.trim();
        for (String allowed : allowedSenders) {
            if (allowed.equalsIgnoreCase(address)) return true;
        }
        return false;
    }

    public List<FilterRule> getRules() {
        return Collections.unmodifiableList(rules);
    }
}
