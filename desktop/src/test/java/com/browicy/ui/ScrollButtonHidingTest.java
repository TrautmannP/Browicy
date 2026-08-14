package com.browicy.ui;

import com.browicy.engine.dom.Document;
import com.browicy.engine.html.HtmlParser;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.PaintFragment;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScrollButtonHidingTest {

    private static final String SHELL = """
            <!DOCTYPE html>
            <html>
            <head>
            <style>
              .viewport {
                position: relative;
                width: %dpx;
                height: 120px;
                background: #fff;
              }
              .content { width: %dpx; height: 80px; background: #eee; }
              .scroll-left, .scroll-right {
                position: absolute;
                top: 0;
                width: 40px;
                height: 100%%;
                background: #fff;
              }
              .scroll-left { left: 0; }
              .scroll-right { right: 0; }
              .control {
                position: absolute;
                left: 200px;
                top: 0;
                width: 20px;
                height: 20px;
                background: #ff0000;
              }
            </style>
            </head>
            <body>
            <div class="viewport">
              <div class="content">content</div>
              <button class="scroll-left"></button>
              <button class="scroll-right"></button>
              <div class="control"></div>
            </div>
            </body>
            </html>
            """;

    private List<PaintFragment> layout(String html) {
        Document doc = new HtmlParser().parse(html);
        DomViewPanel panel = new DomViewPanel(doc);
        try {
            panel.setSize(1280, 720);
            return panel.layoutForTesting(1280, 720).fragments();
        } finally {
            panel.dispose();
        }
    }

    private static boolean hasBoxClass(List<PaintFragment> fragments, String cssClass) {
        for (PaintFragment f : fragments) {
            if (f instanceof BoxFragment box) {
                var source = box.box().source();
                String cls = source == null ? null : source.getAttribute("class");
                if (cls != null && cls.contains(cssClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void overflowingContentKeepsRightButton() {
        List<PaintFragment> fragments = layout(SHELL.formatted(400, 600));
        assertFalse("scroll-left must be hidden at scrollLeft == 0",
                hasBoxClass(fragments, "scroll-left"));
        assertTrue("scroll-right must be visible when content overflows right",
                hasBoxClass(fragments, "scroll-right"));
        assertTrue("unrelated absolute box must stay painted",
                hasBoxClass(fragments, "control"));
    }

    @Test
    public void fittingContentHidesBothButtons() {
        List<PaintFragment> fragments = layout(SHELL.formatted(400, 300));
        assertFalse(hasBoxClass(fragments, "scroll-left"));
        assertFalse(hasBoxClass(fragments, "scroll-right"));
        assertTrue(hasBoxClass(fragments, "control"));
    }

    @Test
    public void exactlyFittingContentHidesRightButton() {
        List<PaintFragment> fragments = layout(SHELL.formatted(400, 400));
        assertFalse(hasBoxClass(fragments, "scroll-left"));
        assertFalse(hasBoxClass(fragments, "scroll-right"));
    }

    @Test
    public void controlBoxIsPaintedEvenWhenButtonsHidden() {
        List<PaintFragment> fragments = layout(SHELL.formatted(400, 300));
        boolean controlFound = false;
        for (PaintFragment f : fragments) {
            if (f instanceof BoxFragment box) {
                var source = box.box().source();
                String cls = source == null ? null : source.getAttribute("class");
                if (cls != null && cls.contains("control")) {
                    controlFound = true;
                    assertEquals("control box width", 20f, box.width(), 0.5f);
                    assertTrue("control box must stay within viewport", box.x() > 0);
                }
            }
        }
        assertTrue("control box must be present", controlFound);
    }

    @Test
    public void noButtonFragmentCoversLeftEdge() {
        List<PaintFragment> fragments = layout(SHELL.formatted(400, 600));
        assertFalse("scroll-left must never be painted at scrollLeft == 0",
                hasBoxClass(fragments, "scroll-left"));
    }
}
