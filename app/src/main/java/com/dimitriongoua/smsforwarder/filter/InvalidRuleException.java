package com.dimitriongoua.smsforwarder.filter;

/** Règle de filtrage impossible à analyser (regex invalide, règle vide…). */
public class InvalidRuleException extends Exception {
    private final String rule;
    private final String reason;

    public InvalidRuleException(String rule, String reason) {
        super("« " + rule + " » : " + reason);
        this.rule = rule;
        this.reason = reason;
    }

    public String getRule() {
        return rule;
    }

    public String getReason() {
        return reason;
    }
}
