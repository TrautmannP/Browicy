package com.browicy.css21;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class ChromeReferenceRenderer implements AutoCloseable {

    private static final List<String> IGNORED_TAGS =
            List.of("html", "head", "title", "meta", "link", "script", "style", "base", "noscript");

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;

    public ChromeReferenceRenderer() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        context = browser.newContext();
    }

    public byte[] screenshot(String url, int width, int height) {
        try (Page page = context.newPage()) {
            page.setViewportSize(width, height);
            page.navigate(url);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            return page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
        }
    }

    public List<ElementGeometrySnapshot> extractGeometry(String url, int width, int height) {
        try (Page page = context.newPage()) {
            page.setViewportSize(width, height);
            page.navigate(url);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            String script = """
                    () => {
                      const PROPERTIES = [
                        'margin-top','margin-right','margin-bottom','margin-left',
                        'padding-top','padding-right','padding-bottom','padding-left',
                        'border-top-width','border-right-width','border-bottom-width','border-left-width',
                        'width','height','display','position','float','overflow-x','overflow-y',
                        'font-size','line-height','text-align','vertical-align','white-space',
                        'background-color','color','z-index','opacity'
                      ];
                      const IGNORED = new Set(['html','head','title','meta','link','script','style','base','noscript']);
                      const getPath = (el) => {
                        if (!el || el.nodeType !== 1) return '';
                        let path = el.tagName.toLowerCase();
                        const attrId = el.getAttribute && el.getAttribute('id');
                        if (attrId) {
                          path += '#' + attrId;
                        } else {
                          const cls = el.getAttribute && el.getAttribute('class');
                          if (cls && cls.trim()) path += '.' + cls.trim().split(/\\s+/).join('.');
                        }
                        const parent = el.parentElement;
                        if (parent && parent.nodeType === 1) {
                          let siblingIndex = 1;
                          let sibling = el.previousElementSibling;
                          while (sibling) {
                            if (sibling.tagName === el.tagName) siblingIndex++;
                            sibling = sibling.previousElementSibling;
                          }
                          path = getPath(parent) + ' > ' + path + ':nth-of-type(' + siblingIndex + ')';
                        }
                        return path;
                      };
                      const result = [];
                      const all = document.querySelectorAll('*');
                      for (let i = 0; i < all.length; i++) {
                        const el = all[i];
                        const tag = el.tagName.toLowerCase();
                        if (IGNORED.has(tag)) continue;
                        const style = window.getComputedStyle(el);
                        if (style.display === 'none' || style.visibility === 'hidden') continue;
                        const rect = el.getBoundingClientRect();
                        const styles = {};
                        for (const p of PROPERTIES) styles[p] = style.getPropertyValue(p);
                        result.push({
                          path: getPath(el),
                          tagName: tag,
                          id: (el.getAttribute && el.getAttribute('id')) || '',
                          className: (el.getAttribute && el.getAttribute('class')) || '',
                          rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
                          computedStyles: styles
                        });
                      }
                      return result;
                    }
                    """;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> raw = (List<Map<String, Object>>) page.evaluate(script);
            List<ElementGeometrySnapshot> snapshots = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rectMap = (Map<String, Object>) item.get("rect");
                ElementGeometrySnapshot.Rect rect = new ElementGeometrySnapshot.Rect(
                        ((Number) rectMap.get("x")).doubleValue(),
                        ((Number) rectMap.get("y")).doubleValue(),
                        ((Number) rectMap.get("width")).doubleValue(),
                        ((Number) rectMap.get("height")).doubleValue());
                @SuppressWarnings("unchecked")
                Map<String, String> styles = (Map<String, String>) item.get("computedStyles");
                snapshots.add(new ElementGeometrySnapshot(
                        (String) item.get("path"),
                        (String) item.get("tagName"),
                        (String) item.get("id"),
                        (String) item.get("className"),
                        rect,
                        styles));
            }
            return snapshots;
        }
    }

    @Override
    public void close() {
        context.close();
        browser.close();
        playwright.close();
    }
}
