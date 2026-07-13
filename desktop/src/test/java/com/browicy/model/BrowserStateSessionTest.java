package com.browicy.model;

import com.browicy.engine.PageSession;
import com.browicy.engine.dom.Document;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BrowserStateSessionTest {

    @Test
    public void navigationClosesThePreviousPageSession() {
        BrowserState state = new BrowserState();
        BrowserTab tab = state.getSelectedTab();
        PageSession session = PageSession.completed(new Document("about:old"));
        tab.attachPageSession(session);

        state.updateUrl(tab.getId(), "about:new");

        assertTrue(session.isClosed());
        assertNull(tab.getPageSession());
    }

    @Test
    public void removingATabClosesItsPageSession() {
        BrowserState state = new BrowserState();
        BrowserTab tab = state.getSelectedTab();
        PageSession session = PageSession.completed(new Document("about:tab"));
        tab.attachPageSession(session);

        state.removeTab(tab.getId());

        assertTrue(session.isClosed());
    }
}
