package com.dimitriongoua.smsforwarder.journal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chiffres de l'Accueil, lus dans le journal ({@link JournalDb#homeStats}). */
public final class HomeStats {
    /** SMS reçus depuis le début du jour et envoyés vers au moins une destination. */
    public int relayedToday;
    /** Envois en échec définitif des SMS reçus depuis le début du jour. */
    public int failedToday;
    /** Envois encore à faire (en attente ou à retenter), tous jours confondus. */
    public int open;
    /** SMS qui ont au moins un envoi à faire, et le plus récent d'entre eux (-1 s'il n'y en a pas). */
    public int openSms;
    public long openSmsId = -1;
    /** Destinations de ces envois à faire, par ordre alphabétique. */
    /** Destinations des envois en attente : clé et titre enregistré avec l'envoi, dans le même ordre. */
    public final List<String> openDestinationKeys = new ArrayList<>();
    public final List<String> openDestinations = new ArrayList<>();
    /** Dernier SMS envoyé vers au moins une destination, null s'il n'y en a pas. */
    public JournalEntry lastRelayed;
    /** Par SIM (identifiant d'abonnement) : SMS relayés depuis le début du jour. */
    public final Map<Integer, Integer> relayedTodayBySim = new HashMap<>();
    /** Par SIM : réception du dernier SMS du journal. */
    public final Map<Integer, Long> lastReceivedBySim = new HashMap<>();
}
