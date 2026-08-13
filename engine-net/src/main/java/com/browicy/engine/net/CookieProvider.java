package com.browicy.engine.net;

import java.net.URI;

/**
 * Liefert die für eine ausgehende HTTP-Anfrage passenden Cookies als
 * {@code Cookie}-Header-Wert (name=value; name2=value2, …). Die Engine
 * verbindet diese Schnittstelle mit dem {@code JsCookieStore}.
 */
@FunctionalInterface
public interface CookieProvider {

    String cookiesFor(URI requestUri);
}
