package com.browicy.integration;

import com.browicy.engine.DocumentUpdateCoordinator;
import com.browicy.engine.InvalidationType;
import com.browicy.engine.PageUpdate;
import com.browicy.engine.css.StyleApplicator;
import com.browicy.engine.dom.Document;
import com.browicy.engine.js.JsExecutionResult;
import com.browicy.engine.render.RenderTree;
import com.browicy.engine.render.RenderTreeBuilder;
import com.browicy.ui.render.RenderLayoutEngine;
import com.browicy.ui.render.RenderLayoutEngine.BoxFragment;
import com.browicy.ui.render.RenderLayoutEngine.LayoutResult;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ReactiveLifecycleIntegrationTest {

    private static final int VIEWPORT_WIDTH = 800;
    private static final int VIEWPORT_HEIGHT = 600;

    @Test
    public void dynamicallyAppendedElementReachesRenderTreeWithCoordinates() {
        String html = """
                <html><head><style>
                  #neu { width: 100px; height: 50px; margin-top: 10px; }
                </style></head>
                <body style="margin: 0"><div id="out"></div><div id="container"></div></body></html>
                """;
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                html, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            List<PageUpdate> updates = new ArrayList<>();
            DocumentUpdateCoordinator coordinator = new DocumentUpdateCoordinator(
                    harness.document(), harness.styleSheets(), new StyleApplicator(),
                    updates::add);
            coordinator.enableNotifications();
            try {
                JsExecutionResult result = harness.execute("""
                        const observer = new MutationObserver(() => {
                          document.getElementById('out').textContent = 'mutiert';
                        });
                        observer.observe(document.getElementById('container'),
                                         { childList: true, subtree: true });
                        const box = document.createElement('div');
                        box.id = 'neu';
                        box.style.cssText = 'width:100px;height:50px;margin-top:10px';
                        document.getElementById('container').appendChild(box);
                        """);
                assertFalse(String.valueOf(result.errors()), result.hasErrors());

                assertEquals("mutiert",
                        harness.document().getElementById("out").getTextContent());

                coordinator.flush();
                assertFalse("Coordinator muss die Mutation liefern", updates.isEmpty());
                PageUpdate update = updates.get(updates.size() - 1);
                assertEquals(harness.document(), update.document());
                assertTrue("ChildListChanged muss mindestens STYLE invalidieren: "
                                + update.invalidation(),
                        update.invalidation().requires(InvalidationType.STYLE));
                assertFalse("Mutation muss im Update gelistet sein",
                        update.mutations().isEmpty());

                LayoutResult layout = layoutOf(harness.document(), harness.styleSheets());
                BoxFragment out = boxById(layout, "out");
                BoxFragment container = boxById(layout, "container");
                BoxFragment neu = boxById(layout, "neu");
                assertNotNull("Das neue Element muss einen Box-Fragment haben", neu);
                assertEquals(10f, container.y() - (out.y() + out.height()), 0.01f);
                assertEquals(container.y(), neu.y(), 0.01f);
                assertEquals(0f, neu.x(), 0.01f);
                assertEquals(100f, neu.width(), 0.01f);
                assertEquals(50f, neu.height(), 0.01f);
            } finally {
                coordinator.close();
            }
        }
    }

    @Test
    public void classToggleReappearsElementAndRecalculatesLayoutHeight() {
        String html = """
                <html><head><style>
                  #target { display: none; }
                  #target.active { display: block; width: 120px; height: 40px; }
                </style></head>
                <body><div id="out"></div><div id="target">sichtbar</div></body></html>
                """;
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                html, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            LayoutResult before = layoutOf(harness.document(), harness.styleSheets());
            assertFalse("display:none darf kein Fragment erzeugen",
                    hasBox(before, "target"));
            float heightBefore = before.height();

            List<PageUpdate> updates = new ArrayList<>();
            DocumentUpdateCoordinator coordinator = new DocumentUpdateCoordinator(
                    harness.document(), harness.styleSheets(), new StyleApplicator(),
                    updates::add);
            coordinator.enableNotifications();
            try {
                JsExecutionResult result = harness.execute("""
                        const el = document.getElementById('target');
                        el.classList.toggle('active');
                        """);
                assertFalse(String.valueOf(result.errors()), result.hasErrors());
                coordinator.flush();

                assertFalse("Coordinator muss die Klassen-Mutation liefern",
                        updates.isEmpty());
                assertTrue(updates.get(updates.size() - 1).invalidation()
                        .requires(InvalidationType.STYLE));

                LayoutResult after = layoutOf(harness.document(), harness.styleSheets());
                BoxFragment target = boxById(after, "target");
                assertNotNull("Element muss nach dem Toggle ein Fragment haben", target);
                assertEquals(120f, target.width(), 0.01f);
                assertEquals(40f, target.height(), 0.01f);
                assertTrue("Layout-Höhe muss durch das sichtbare Element wachsen",
                        after.height() > heightBefore);
            } finally {
                coordinator.close();
            }
        }
    }

    @Test
    public void removedElementDisappearsFromLayout() {
        String html = """
                <html><head><style>
                  #weg { width: 80px; height: 30px; }
                </style></head>
                <body><div id="out"></div>
                  <div id="wrap"><div id="weg">weg</div></div></body></html>
                """;
        try (JsLayoutHarness harness = JsLayoutHarness.open(
                html, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            assertTrue(hasBox(layoutOf(harness.document(), harness.styleSheets()), "weg"));

            JsExecutionResult result = harness.execute("""
                    const el = document.getElementById('weg');
                    el.remove();
                    """);
            assertFalse(String.valueOf(result.errors()), result.hasErrors());

            assertFalse("Entferntes Element darf kein Fragment mehr haben",
                    hasBox(layoutOf(harness.document(), harness.styleSheets()), "weg"));
        }
    }

    private static LayoutResult layoutOf(Document document,
                                         com.browicy.engine.css.StyleSheetRegistry styleSheets) {
        new StyleApplicator().apply(document, styleSheets, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        RenderTree tree = new RenderTreeBuilder().build(document, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            return new RenderLayoutEngine().layout(
                    tree, VIEWPORT_WIDTH, new Insets(0, 0, 0, 0), graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static boolean hasBox(LayoutResult layout, String id) {
        return layout.fragments().stream()
                .filter(BoxFragment.class::isInstance)
                .map(BoxFragment.class::cast)
                .anyMatch(fragment -> fragment.box().source() != null
                        && id.equals(fragment.box().source().getAttribute("id")));
    }

    private static BoxFragment boxById(LayoutResult layout, String id) {
        return layout.fragments().stream()
                .filter(BoxFragment.class::isInstance)
                .map(BoxFragment.class::cast)
                .filter(fragment -> fragment.box().source() != null)
                .filter(fragment -> id.equals(fragment.box().source().getAttribute("id")))
                .findFirst()
                .orElse(null);
    }
}
