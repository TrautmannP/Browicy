package com.browicy.engine.net;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lädt eine Seite über den {@link HttpClient}: normalisiert die eingegebene
 * URL, folgt Weiterleitungen (z.&nbsp;B. {@code http://google.com} →
 * {@code http://www.google.com/}) und dekodiert den Rumpf mit dem passenden
 * Zeichensatz zu HTML-Quelltext.
 */
public final class PageLoader {

    /** Ergebnis eines Seitenladevorgangs: finale URL nach Redirects, Status und HTML. */
    public record Page(URI uri, int statusCode, String html) {
    }

    private static final int MAX_REDIRECTS = 10;

    /** URLs mit Schema beginnen mit z.B. "http:"; alles andere bekommt "http://" vorangestellt. */
    private static final Pattern HAS_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*:");

    /** Zeichensatz-Angabe im Dokument selbst: {@code <meta charset=...>} oder http-equiv-Variante. */
    private static final Pattern META_CHARSET = Pattern.compile(
            "<meta[^>]+charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-.:]+)", Pattern.CASE_INSENSITIVE);

    /** Nur der Anfang des Dokuments wird nach einer meta-charset-Angabe durchsucht. */
    private static final int CHARSET_SNIFF_BYTES = 2048;

    private final HttpClient client;

    public PageLoader() {
        this(new HttpClient());
    }

    public PageLoader(HttpClient client) {
        this.client = client;
    }

    /**
     * Ergänzt fehlende Schemata ({@code google.com} → {@code http://google.com})
     * und parst die Eingabe zu einer URI.
     *
     * @throws IllegalArgumentException bei leerer oder syntaktisch ungültiger Eingabe
     */
    public static URI normalize(String input) {
        String trimmed = input == null ? "" : input.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Leere URL");
        }
        if (!HAS_SCHEME.matcher(trimmed).find()) {
            trimmed = "http://" + trimmed;
        }
        return URI.create(trimmed);
    }

    /** Lädt die Seite hinter der URL und folgt dabei bis zu {@value #MAX_REDIRECTS} Weiterleitungen. */
    public Page load(String url) throws IOException {
        URI uri = normalize(url);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpResponse response = client.get(uri);
            String location = response.location();
            if (response.isRedirect() && location != null) {
                uri = uri.resolve(location.strip());
                continue;
            }
            return new Page(uri, response.statusCode(), decodeHtml(response));
        }
        throw new IOException("Zu viele Weiterleitungen (mehr als " + MAX_REDIRECTS + "): " + url);
    }

    private static String decodeHtml(HttpResponse response) {
        Charset charset = response.charsetFromHeaders()
                .or(() -> sniffMetaCharset(response.body()))
                .orElse(StandardCharsets.UTF_8);
        return new String(response.body(), charset);
    }

    private static Optional<Charset> sniffMetaCharset(byte[] body) {
        // ASCII-kompatible Vorschau reicht, um <meta charset=...> zu finden
        String prefix = new String(body, 0, Math.min(body.length, CHARSET_SNIFF_BYTES),
                StandardCharsets.ISO_8859_1);
        Matcher matcher = META_CHARSET.matcher(prefix);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Charset.forName(matcher.group(1)));
        } catch (IllegalArgumentException unknownCharset) {
            return Optional.empty();
        }
    }
}
