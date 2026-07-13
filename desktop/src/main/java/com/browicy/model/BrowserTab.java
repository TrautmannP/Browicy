package com.browicy.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public final class BrowserTab {

    public static final String ABOUT_BLANK = "about:blank";

    private final String id = UUID.randomUUID().toString();
    private String title = "Neuer Tab";
    private String url = ABOUT_BLANK;

    public boolean isBlank() {
        return ABOUT_BLANK.equals(url);
    }
}
