package com.dimitriongoua.smsforwarder.journal;

import java.util.ArrayList;
import java.util.List;

/** Un SMS traité par l'application et le résultat de son envoi vers chaque destination. */
public final class JournalEntry {
    public final long id;
    public final String sender;
    public final String body;
    /** Horodatage du SMS (centre SMS), envoyé tel quel aux destinations. */
    public final long timestamp;
    /** Heure de réception sur le téléphone. */
    public final long receivedAt;
    public final int subscriptionId;
    public final String simLabel;
    public final String source;
    public int simSlot = -1;
    public String simCarrier;
    public String simNumber;
    public final List<Delivery> deliveries = new ArrayList<>();

    public JournalEntry(long id, String sender, String body, long timestamp, long receivedAt,
                        int subscriptionId, String simLabel, String source) {
        this.id = id;
        this.sender = sender;
        this.body = body;
        this.timestamp = timestamp;
        this.receivedAt = receivedAt;
        this.subscriptionId = subscriptionId;
        this.simLabel = simLabel;
        this.source = source;
    }
}
