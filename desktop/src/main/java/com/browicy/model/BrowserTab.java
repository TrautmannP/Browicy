package com.browicy.model;

import java.util.UUID;

/**
 * Ein Browser-Tab mit Titel und aktueller URL.
 */
public final class BrowserTab {

    public static final String ABOUT_BLANK = "about:blank";

    private final String id = UUID.randomUUID().toString();
    private String title = "Neuer Tab";
    private String url = ABOUT_BLANK;

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isBlank() {
        return ABOUT_BLANK.equals(url);
    }
}
