package com.browicy.engine.js;

import java.util.List;
import java.util.Objects;

public record JsFetchResponse(String url,
                              int status,
                              String statusText,
                              List<Header> headers,
                              String bodyText) {

    public JsFetchResponse {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(statusText, "statusText");
        Objects.requireNonNull(bodyText, "bodyText");
        headers = List.copyOf(headers);
    }

    public record Header(String name, String value) {
        public Header {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }
}
