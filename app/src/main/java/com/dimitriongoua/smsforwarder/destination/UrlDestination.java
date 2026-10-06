package com.dimitriongoua.smsforwarder.destination;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Destination URL : chaque SMS relayé y est envoyé en POST, au format JSON complet
 * { from, body, timestamp, sim, device }, avec un en-tête de sécurité optionnel.
 * Classe sans dépendance Android, testée par UrlDestinationTest.
 */
public final class UrlDestination {
    // Caractères autorisés dans un nom d'en-tête HTTP (« token » de la RFC 9110).
    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");

    private final String id;
    /** Titre affiché (journal, détail, destinations) ; null : titre par défaut. */
    private final String title;
    private final String url;
    private final String headerName;
    private final String headerValue;
    private final boolean enabled;

    public UrlDestination(String id, String url, String headerName, String headerValue, boolean enabled) {
        this(id, null, url, headerName, headerValue, enabled);
    }

    public UrlDestination(String id, String title, String url, String headerName, String headerValue, boolean enabled) {
        this.id = id == null || id.trim().isEmpty() ? UUID.randomUUID().toString() : id;
        this.title = blankToNull(title);
        this.url = url == null ? "" : url.trim();
        this.headerName = blankToNull(headerName);
        // Sans nom d'en-tête, la valeur n'a pas de sens : elle n'est pas conservée.
        this.headerValue = this.headerName == null ? null : (headerValue == null ? "" : headerValue);
        this.enabled = enabled;
    }

    /** @return le message d'erreur, ou null si l'URL est une URL http(s) absolue */
    public static String validateUrl(String url) {
        String value = url == null ? "" : url.trim();
        if (value.isEmpty()) return "URL manquante";
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!uri.isAbsolute() || !(scheme.equals("https") || scheme.equals("http"))) {
                return "URL absolue attendue (https://…)";
            }
            if (uri.getHost() == null || uri.getHost().isEmpty()) return "Nom de domaine manquant";
        } catch (URISyntaxException e) {
            return "URL invalide";
        }
        return null;
    }

    /** @return le message d'erreur, ou null si le nom est vide (pas d'en-tête) ou valide */
    public static String validateHeaderName(String name) {
        String value = blankToNull(name);
        if (value == null) return null;
        return HEADER_NAME.matcher(value).matches() ? null : "Nom d'en-tête invalide (ex. X-Sms-Token)";
    }

    /** @return la première erreur de la destination, ou null si elle est valide */
    public String validate() {
        String error = validateUrl(url);
        return error != null ? error : validateHeaderName(headerName);
    }

    /**
     * URL affichable (journal, écran des destinations) : la requête et le fragment, qui
     * peuvent contenir un secret, sont masqués.
     */
    public String getLabel() {
        try {
            URI uri = new URI(url);
            if (uri.getHost() == null) return url;
            String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            String hidden = uri.getRawQuery() != null || uri.getRawFragment() != null ? "?…" : "";
            return uri.getScheme() + "://" + uri.getHost() + port + path + hidden;
        } catch (URISyntaxException e) {
            return url;
        }
    }

    /** Clé de la destination dans le journal des envois. */
    public String getKey() {
        return "url:" + id;
    }

    public boolean hasHeader() {
        return headerName != null;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getHeaderName() {
        return headerName;
    }

    public String getHeaderValue() {
        return headerValue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UrlDestination withEnabled(boolean value) {
        return new UrlDestination(id, title, url, headerName, headerValue, value);
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
