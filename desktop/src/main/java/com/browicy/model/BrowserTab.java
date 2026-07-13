package com.browicy.model;

import com.browicy.engine.PageSession;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
public final class BrowserTab {

    public static final String ABOUT_BLANK = "about:blank";

    private final String id = UUID.randomUUID().toString();
    private String title = "Neuer Tab";
    private String url = ABOUT_BLANK;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private PageSession pageSession;

    public PageSession getPageSession() {
        return pageSession;
    }

    public void attachPageSession(PageSession session) {
        Objects.requireNonNull(session, "session");
        if (pageSession == session) {
            return;
        }
        closePageSession();
        pageSession = session;
    }

    public void closePageSession() {
        PageSession current = pageSession;
        pageSession = null;
        if (current != null) {
            current.close();
        }
    }

    public boolean isBlank() {
        return ABOUT_BLANK.equals(url);
    }
}
