package com.browicy.engine.js;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class JsCookieStore {

    static final int MAX_COOKIES = 256;
    static final int MAX_NAME_VALUE_BYTES = 4096;
    private static final Duration MAX_LIFETIME = Duration.ofDays(400);

    private static final DateTimeFormatter LEGACY_EXPIRES_FORMAT = DateTimeFormatter.ofPattern(
            "EEE, dd-MMM-yyyy HH:mm:ss zzz", Locale.ROOT);

    private final Clock clock;
    private final Map<CookieKey, StoredCookie> cookies = new LinkedHashMap<>();
    private long nextCreationOrder;

    public JsCookieStore() {
        this(Clock.systemUTC());
    }

    JsCookieStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void storeFromHttp(URI responseUri, String setCookieHeader) {
        store(responseUri, setCookieHeader, true);
    }

    public void storeFromScript(URI documentUri, String cookieString) {
        store(documentUri, cookieString, false);
    }

    public synchronized String cookiesForScript(URI documentUri) {
        String host = hostOf(documentUri);
        if (host == null) {
            return "";
        }
        boolean secureContext = isSecure(documentUri);
        String requestPath = requestPathOf(documentUri);
        purgeExpired(clock.instant());
        List<StoredCookie> matching = new ArrayList<>();
        for (StoredCookie cookie : cookies.values()) {
            if (cookie.httpOnly()) {
                continue;
            }
            if (cookie.secure() && !secureContext) {
                continue;
            }
            boolean domainOk = cookie.hostOnly()
                    ? host.equals(cookie.domain())
                    : domainMatches(host, cookie.domain());
            if (!domainOk || !pathMatches(requestPath, cookie.path())) {
                continue;
            }
            matching.add(cookie);
        }
        matching.sort(Comparator
                .comparingInt((StoredCookie cookie) -> cookie.path().length()).reversed()
                .thenComparingLong(StoredCookie::creationOrder));
        StringBuilder result = new StringBuilder();
        for (StoredCookie cookie : matching) {
            if (result.length() > 0) {
                result.append("; ");
            }
            if (!cookie.name().isEmpty()) {
                result.append(cookie.name()).append('=');
            }
            result.append(cookie.value());
        }
        return result.toString();
    }

    private synchronized void store(URI uri, String cookieString, boolean fromHttp) {
        if (uri == null || cookieString == null) {
            return;
        }
        String host = hostOf(uri);
        if (host == null) {
            return;
        }

        String[] segments = cookieString.split(";", -1);
        String name;
        String value;
        int separator = segments[0].indexOf('=');
        if (separator < 0) {
            name = "";
            value = segments[0].strip();
        } else {
            name = segments[0].substring(0, separator).strip();
            value = segments[0].substring(separator + 1).strip();
        }
        if (name.isEmpty() && value.isEmpty()) {
            return;
        }
        if (!isValidName(name) || containsForbiddenCharacters(value)) {
            return;
        }
        if (name.getBytes(StandardCharsets.UTF_8).length
                + value.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_VALUE_BYTES) {
            return;
        }

        String domainAttribute = null;
        String pathAttribute = null;
        boolean secure = false;
        boolean httpOnly = false;
        Long maxAgeSeconds = null;
        Instant expiresAttribute = null;
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i].strip();
            int attrSeparator = segment.indexOf('=');
            String attrName = (attrSeparator < 0 ? segment : segment.substring(0, attrSeparator))
                    .strip().toLowerCase(Locale.ROOT);
            String attrValue = attrSeparator < 0 ? "" : segment.substring(attrSeparator + 1).strip();
            switch (attrName) {
                case "domain" -> {
                    String candidate = attrValue.toLowerCase(Locale.ROOT);
                    if (candidate.startsWith(".")) {
                        candidate = candidate.substring(1);
                    }
                    if (!candidate.isEmpty()) {
                        domainAttribute = candidate;
                    }
                }
                case "path" -> pathAttribute = attrValue;
                case "secure" -> secure = true;
                case "httponly" -> httpOnly = true;
                case "max-age" -> maxAgeSeconds = parseMaxAge(attrValue);
                case "expires" -> expiresAttribute = parseExpires(attrValue);
                default -> { }
            }
        }

        if (httpOnly && !fromHttp) {
            return;
        }
        boolean secureContext = isSecure(uri);
        if (secure && !secureContext) {
            return;
        }

        String domain;
        boolean hostOnly;
        if (domainAttribute != null) {
            if (!isAllowedDomainAttribute(host, domainAttribute)) {
                return;
            }
            domain = domainAttribute;
            hostOnly = false;
        } else {
            domain = host;
            hostOnly = true;
        }

        String path = pathAttribute != null && pathAttribute.startsWith("/")
                ? pathAttribute : defaultPathOf(uri);

        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.startsWith("__secure-") && !secure) {
            return;
        }
        if (lowerName.startsWith("__host-")
                && !(secure && hostOnly && path.equals("/"))) {
            return;
        }

        CookieKey key = new CookieKey(name, domain, path);
        StoredCookie existing = cookies.get(key);
        if (existing != null && existing.httpOnly() && !fromHttp) {
            return;
        }

        Instant now = clock.instant();
        Instant expiresAt = null;
        if (maxAgeSeconds != null) {
            expiresAt = maxAgeSeconds <= 0
                    ? Instant.EPOCH
                    : now.plus(clampLifetime(Duration.ofSeconds(maxAgeSeconds)));
        } else if (expiresAttribute != null) {
            expiresAt = expiresAttribute.isAfter(now.plus(MAX_LIFETIME))
                    ? now.plus(MAX_LIFETIME) : expiresAttribute;
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            cookies.remove(key);
            return;
        }

        purgeExpired(now);
        if (existing == null && cookies.size() >= MAX_COOKIES) {
            return;
        }
        long creationOrder = existing == null ? nextCreationOrder++ : existing.creationOrder();
        cookies.put(key, new StoredCookie(
                name, value, domain, path, hostOnly, secure, httpOnly, expiresAt, creationOrder));
    }

    private void purgeExpired(Instant now) {
        cookies.values().removeIf(cookie ->
                cookie.expiresAt() != null && !cookie.expiresAt().isAfter(now));
    }

    private static Long parseMaxAge(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static Instant parseExpires(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException firstFormat) {
            try {
                return ZonedDateTime.parse(value, LEGACY_EXPIRES_FORMAT).toInstant();
            } catch (DateTimeParseException secondFormat) {
                return null;
            }
        }
    }

    private static Duration clampLifetime(Duration lifetime) {
        return lifetime.compareTo(MAX_LIFETIME) > 0 ? MAX_LIFETIME : lifetime;
    }

    private static boolean isValidName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (character <= 0x20 || character == 0x7F
                    || "()<>@,;:\\\"/[]?={}".indexOf(character) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsForbiddenCharacters(String text) {
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character < 0x20 || character == 0x7F) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedDomainAttribute(String host, String domainAttribute) {
        if (host.equals(domainAttribute)) {
            return true;
        }
        if (isIpAddress(host)) {
            return false;
        }
        return domainAttribute.contains(".") && domainMatches(host, domainAttribute);
    }

    private static boolean domainMatches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static boolean isIpAddress(String host) {
        if (host.contains(":") || host.startsWith("[")) {
            return true;
        }
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static boolean pathMatches(String requestPath, String cookiePath) {
        if (requestPath.equals(cookiePath)) {
            return true;
        }
        if (!requestPath.startsWith(cookiePath)) {
            return false;
        }
        return cookiePath.endsWith("/") || requestPath.charAt(cookiePath.length()) == '/';
    }

    private static String hostOf(URI uri) {
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return null;
        }
        String host = uri.getHost();
        return host == null ? null : host.toLowerCase(Locale.ROOT);
    }

    private static boolean isSecure(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme());
    }

    private static String requestPathOf(URI uri) {
        String path = uri.getPath();
        return path == null || path.isEmpty() || !path.startsWith("/") ? "/" : path;
    }

    private static String defaultPathOf(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty() || !path.startsWith("/")) {
            return "/";
        }
        int lastSlash = path.lastIndexOf('/');
        return lastSlash == 0 ? "/" : path.substring(0, lastSlash);
    }

    private record CookieKey(String name, String domain, String path) {
    }

    private record StoredCookie(String name,
                                String value,
                                String domain,
                                String path,
                                boolean hostOnly,
                                boolean secure,
                                boolean httpOnly,
                                Instant expiresAt,
                                long creationOrder) {
    }
}
