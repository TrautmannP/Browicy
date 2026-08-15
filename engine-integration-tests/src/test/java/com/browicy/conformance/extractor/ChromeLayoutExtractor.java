package com.browicy.conformance.extractor;

import com.browicy.conformance.model.ElementLayoutBox;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Extracts browser-native geometry and computed styles from Chromium. */
public final class ChromeLayoutExtractor implements AutoCloseable {
    private static final String EXTRACT_SCRIPT = """
            () => {
              const styleProperties = [
                'display', 'position', 'margin', 'padding', 'border-width',
                'font-size', 'font-family', 'font-weight', 'line-height',
                'box-sizing', 'color', 'background-color', 'flex-direction',
                'flex-wrap', 'justify-content', 'align-items', 'gap',
                'grid-template-columns', 'grid-template-rows'
              ];
              const cssEscape = value => {
                let result = '';
                for (let index = 0; index < value.length; index++) {
                  const code = value.codePointAt(index);
                  if (code > 0xffff) index++;
                  const first = result.length === 0;
                  if (code === 0) {
                    result += '\\ufffd';
                  } else if ((first && code >= 48 && code <= 57)
                      || code < 0x20 || code === 0x7f) {
                    result += '\\\\' + code.toString(16) + ' ';
                  } else if ((code === 45 && !(first && value.length === 1))
                      || code === 95
                      || code >= 48 && code <= 57
                      || code >= 65 && code <= 90
                      || code >= 97 && code <= 122
                      || code >= 0x80) {
                    result += String.fromCodePoint(code);
                  } else {
                    result += '\\\\' + String.fromCodePoint(code);
                  }
                }
                return result;
              };
              const uniqueId = id => {
                let count = 0;
                for (const candidate of document.querySelectorAll('[id]')) {
                  if (candidate.id === id && ++count > 1) return false;
                }
                return count === 1;
              };
              const selectorFor = element => {
                const parts = [];
                let current = element;
                while (current && current.nodeType === Node.ELEMENT_NODE) {
                  if (current.id && uniqueId(current.id)) {
                    parts.unshift('#' + cssEscape(current.id));
                    break;
                  }
                  let index = 1;
                  for (let sibling = current.previousElementSibling;
                       sibling; sibling = sibling.previousElementSibling) index++;
                  parts.unshift(current.tagName.toLowerCase() + ':nth-child(' + index + ')');
                  current = current.parentElement;
                }
                return parts.join(' > ');
              };
              const result = [];
              for (const element of document.querySelectorAll('*')) {
                const computed = getComputedStyle(element);
                if (computed.display === 'none' || computed.display === 'contents') continue;
                const rect = element.getBoundingClientRect();
                const styles = {};
                for (const property of styleProperties) {
                  styles[property] = computed.getPropertyValue(property).trim();
                }
                result.push({
                  selector: selectorFor(element),
                  tagName: element.tagName.toLowerCase(),
                  x: rect.x,
                  y: rect.y,
                  width: rect.width,
                  height: rect.height,
                  computedStyles: styles
                });
              }
              return result;
            }
            """;

    private final Playwright playwright;
    private final Browser browser;

    public ChromeLayoutExtractor() {
        this(true);
    }

    public ChromeLayoutExtractor(boolean headless) {
        playwright = Playwright.create();
        try {
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(headless));
        } catch (RuntimeException failure) {
            playwright.close();
            throw failure;
        }
    }

    public Map<String, ElementLayoutBox> extract(String htmlContentOrUrl,
                                                  int viewportWidth,
                                                  int viewportHeight) {
        validateViewport(viewportWidth, viewportHeight);
        Objects.requireNonNull(htmlContentOrUrl, "htmlContentOrUrl");
        try (BrowserContext context = newContext(viewportWidth, viewportHeight)) {
            Page page = context.newPage();
            load(page, htmlContentOrUrl);
            Object raw = page.evaluate(EXTRACT_SCRIPT);
            return decode(raw);
        }
    }

    public byte[] captureScreenshot(String htmlContentOrUrl,
                                    int viewportWidth,
                                    int viewportHeight) {
        validateViewport(viewportWidth, viewportHeight);
        Objects.requireNonNull(htmlContentOrUrl, "htmlContentOrUrl");
        try (BrowserContext context = newContext(viewportWidth, viewportHeight)) {
            Page page = context.newPage();
            load(page, htmlContentOrUrl);
            return page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        }
    }

    private BrowserContext newContext(int viewportWidth, int viewportHeight) {
        return browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(viewportWidth, viewportHeight));
    }

    private static void load(Page page, String htmlContentOrUrl) {
        String input = htmlContentOrUrl.strip();
        if (input.startsWith("<")) {
            page.setContent(input,
                    new Page.SetContentOptions().setWaitUntil(WaitUntilState.LOAD));
        } else {
            page.navigate(navigationTarget(input),
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private static String navigationTarget(String input) {
        try {
            Path path = Path.of(input);
            if (Files.isRegularFile(path)) {
                return path.toAbsolutePath().toUri().toString();
            }
        } catch (RuntimeException ignored) {
            // The input is a normal URL rather than a local path.
        }
        return input;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ElementLayoutBox> decode(Object raw) {
        if (!(raw instanceof List<?> rows)) {
            throw new IllegalStateException("Chromium returned a non-list layout payload");
        }
        Map<String, ElementLayoutBox> result = new LinkedHashMap<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> values)) {
                throw new IllegalStateException("Chromium returned a malformed layout row");
            }
            String selector = stringValue(values.get("selector"));
            Map<String, String> styles = new LinkedHashMap<>();
            Object rawStyles = values.get("computedStyles");
            if (rawStyles instanceof Map<?, ?> styleMap) {
                for (Map.Entry<?, ?> entry : styleMap.entrySet()) {
                    styles.put(String.valueOf(entry.getKey()), stringValue(entry.getValue()));
                }
            }
            ElementLayoutBox box = new ElementLayoutBox(
                    selector,
                    stringValue(values.get("tagName")),
                    numberValue(values.get("x")),
                    numberValue(values.get("y")),
                    numberValue(values.get("width")),
                    numberValue(values.get("height")),
                    styles);
            if (result.put(selector, box) != null) {
                throw new IllegalStateException("Chromium generated a duplicate selector: " + selector);
            }
        }
        return result;
    }

    private static String stringValue(Object value) {
        return Objects.toString(value, "");
    }

    private static float numberValue(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return Float.parseFloat(stringValue(value));
    }

    private static void validateViewport(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("viewport dimensions must be positive");
        }
    }

    @Override
    public void close() {
        browser.close();
        playwright.close();
    }
}
