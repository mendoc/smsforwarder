package com.dimitriongoua.smsforwarder.filter;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Règle de filtrage avancée, écrite sur une ligne :
 * <ul>
 *     <li>{@code from:<regex>} s'applique à l'expéditeur ;</li>
 *     <li>{@code body:<regex>} s'applique au corps du SMS ;</li>
 *     <li>{@code <regex>} sans préfixe s'applique à l'expéditeur ou au corps ;</li>
 *     <li>un {@code !} en tête en fait une règle d'exclusion.</li>
 * </ul>
 * La regex est cherchée n'importe où dans le texte ({@link java.util.regex.Matcher#find()}).
 * Classe sans dépendance Android, testée par FilterRuleTest.
 */
public final class FilterRule {

    public enum Target { FROM, BODY, ANY }

    private static final String FROM_PREFIX = "from:";
    private static final String BODY_PREFIX = "body:";

    private final String source;
    private final boolean exclusion;
    private final Target target;
    private final Pattern pattern;

    private FilterRule(String source, boolean exclusion, Target target, Pattern pattern) {
        this.source = source;
        this.exclusion = exclusion;
        this.target = target;
        this.pattern = pattern;
    }

    /**
     * Analyse une ligne de règle.
     *
     * @throws InvalidRuleException si la ligne est vide ou si la regex est invalide
     */
    public static FilterRule parse(String line) throws InvalidRuleException {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty()) throw new InvalidRuleException(text, "règle vide");

        String rest = text;
        boolean exclusion = rest.startsWith("!");
        if (exclusion) rest = rest.substring(1);

        Target target = Target.ANY;
        if (rest.startsWith(FROM_PREFIX)) {
            target = Target.FROM;
            rest = rest.substring(FROM_PREFIX.length());
        } else if (rest.startsWith(BODY_PREFIX)) {
            target = Target.BODY;
            rest = rest.substring(BODY_PREFIX.length());
        }
        if (rest.isEmpty()) throw new InvalidRuleException(text, "regex manquante");

        try {
            return new FilterRule(text, exclusion, target, Pattern.compile(rest));
        } catch (PatternSyntaxException e) {
            throw new InvalidRuleException(text, e.getDescription());
        }
    }

    public boolean matches(String from, String body) {
        switch (target) {
            case FROM:
                return find(from);
            case BODY:
                return find(body);
            default:
                return find(from) || find(body);
        }
    }

    private boolean find(String text) {
        return text != null && pattern.matcher(text).find();
    }

    public boolean isExclusion() {
        return exclusion;
    }

    public Target getTarget() {
        return target;
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return source;
    }
}
