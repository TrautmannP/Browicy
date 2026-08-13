package com.browicy.engine.net;

import java.net.URI;

/**
 * Nimmt {@code Set-Cookie}-Header von HTTP-Antworten entgegen. Die Engine
 * verbindet diese Schnittstelle mit dem {@code JsCookieStore}.
 */
@FunctionalInterface
public interface CookieSink {

    void store(URI responseUri, String setCookieHeader);
}
