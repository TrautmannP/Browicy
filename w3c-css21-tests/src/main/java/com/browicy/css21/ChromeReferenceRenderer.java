package com.browicy.css21;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Rendert Testseiten in headless Chromium (Playwright) und liefert
 * Viewport-Screenshots als PNG — die „so soll es aussehen"-Referenz.
 */
public final class ChromeReferenceRenderer implements AutoCloseable {

    private final Playwright playwright;
    private final Browser browser;

    public ChromeReferenceRenderer() {
        playwright = Playwright.create();
        try {
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
        } catch (RuntimeException failure) {
            playwright.close();
            throw failure;
        }
    }

    /** Viewport-Screenshot der URL in der angegebenen Größe. */
    public byte[] screenshot(String url, int width, int height) {
        try (BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setViewportSize(width, height))) {
            Page page = context.newPage();
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
            page.waitForLoadState(LoadState.NETWORKIDLE);
            return page.screenshot();
        }
    }

    @Override
    public void close() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
