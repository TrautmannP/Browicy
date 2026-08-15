# Browser engine progress workflow

`browser-cli` is Browicy's automation boundary. It loads pages through `BrowicyEngine`,
waits for resources and the JavaScript event loop, builds the production render tree, and
writes one JSON report. It does not use a separate test-only rendering path.

## Reproduce the CSS3Test baseline

Build the inspector, then load the CSS3Test CSS-2007 filter:

```bash
mvn -pl browser-cli -am package -DskipTests
java --sun-misc-unsafe-memory-access=allow -jar browser-cli/target/browicy-inspect.jar \
  "https://css3test.com/?filter=css2007" --output target/css3test-css2007.json
```

Baseline recorded on 2026-08-13 (nach JS/CSS-Ausbau für Vue 3):

- CSS3Test CSS-2007 reports **94/94 passed (100%)** — alle 48 offenen Fälle
  des CSS-2007-Zielkatalogs umgesetzt (46/94 → 94/94). Neu: Attribut-Präfix/
  Suffix-Selektoren `[att^=val]`/`[att$=val]`, `:nth-last-child()`,
  `:nth-last-of-type()`, `:only-of-type`, `:empty`, `::first-letter`/
  `::first-line`, `:link`/`:visited`/`:target`/`:indeterminate`, HSL/HSLA,
  `currentColor`, `opacity`-Clamping sowie `@namespace`-Statements und
  Namespace-Selektoren (`*|html`, `[*|attr]`).
- Acid3: **65/100 passed** (unverändert, keine Regressionen).
- Insgesamt **159/194** Fälle (vorher 111/194).

Die Testseite und ihr Inhalt können sich ändern; Werte sind als
Regressions-Baseline zu verstehen, nicht als dauerhafte Erwartung.

## Vue 3 Demo als Entwicklungs-Smoke-Test

Die Demo unter `artifacts/vue-demo/` (Vue 3.5 global build, Zähler, Todo-Liste,
v-model, v-for, Klassen-/Stil-Bindungen) wird lokal über einen HTTP-Server
ausgeliefert und mit dem Inspector geprüft:

```bash
python -m http.server 8137 --bind 127.0.0.1 --directory artifacts/vue-demo
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
  -jar browser-cli/target/browicy-inspect.jar \
  "http://127.0.0.1:8137/index.html" --output artifacts/vue3-report.json \
  --screenshot artifacts/vue3-final.png --viewport 640x900
```

Stand 2026-08-13: `healthy: true`, keine JavaScript-Fehler, keine
Compatibility-Findings, 75 DOM-Knoten, 13 akzeptierte CSS-Regeln,
51 Render-Knoten mit 18 Textläufen. Der end-to-end Vue-Mount (inklusive
Reaktivität per `dispatchEvent` und `nextTick`) ist als Regressionstest in
`engine-js` untergebracht (`JavaScriptEngineTest.mountsAVue3Application…`).

## Agent and CI loop

1. Run the inspector before a change and retain its JSON report.
2. Implement one capability in the smallest suitable engine module.
3. Add a deterministic regression test for that capability.
4. Run `mvn verify`.
5. Run the inspector again and compare `page.detectedScore`, `dom`, `css`, `renderTree`,
   `javascript.errors`, and failed `network` events.

JavaScript errors include source URL, line, and column. They are usually the most direct
indication of the next missing capability. Tests must remain conservative: `CSS.supports`
reports only syntax/value pairs the engine accepts, so the external score cannot claim
unimplemented CSS support.

## Combined CSS3Test and Acid3 report

Run the compatibility-report profile to execute the live CSS3Test filter and the embedded
Acid3 harness during Maven's `verify` lifecycle:

```bash
mvn -Pcompatibility-report -pl acid3-tests -am verify
```

The run on 2026-08-13 recorded 94/94 CSS3Test cases (100%) and 65/100 Acid3 subtests,
for 159/194 cases overall (vorher: 46/94, 65/100, 111/194). Reports are written to
`target/compatibility-reports`: `latest.html` is intended for human review, while
`latest.json` is intended for CI and automated analysis. Timestamped copies are retained
for trend tracking.

## CSS transform support (2026-08-14)

`transform` and `transform-origin` are parsed by CssParser (validated function lists:
translate/translateX/translateY/rotate/scale/scaleX/scaleY/matrix), resolved into a
`Transform` record (engine-render) and painted as an AffineTransform about the box
origin by the layout engine. Percentages resolve against the box's own width/height;
transform-origin supports keywords, lengths and percentages. Function names are
case-insensitive; unitless lengths are only valid as 0.

Known approximation: the transform is applied at paint time only; layout coordinates
and hit-testing are unaffected, and absolutely positioned descendants of a transformed
box are not transformed (they are painted after their containing block's fragment range).

Known flaky test (snapshot-race family, passes isolated and on retry):
`DomViewPanelTest.laysOutAndPaintsInlineBackgroundPaddingAndBorder`.

## wetter.de-Rendering (2026-08-15)

Entwicklungsziel: `www.wetter.de` (Nuxt/Vue 3 SSR + Tailwind + Slick-Slider, API-geladene
Wetterdaten) korrekt darstellen. Baseline war eine komplett leere Seite trotz
2075 DOM-Knoten. Referenz via Chromium (`xd://browser` auf das Playwright-Chromium),
Verifikation über `browicy-inspect.jar`. Gefunden und behoben:

- **Hex-Escapes im SelectorParser**: Tailwind-Arbitrary-Klassen wie
  `.lg\:grid-cols-\[minmax\(100px\2c 1fr\)_…\]` (Komma als `\2c `) wurden verworfen.
  `decodeEscape()` dekodiert jetzt 1–6 Hex-Ziffern mit Whitespace-Terminator, U+FFFD
  für invalide Sequenzen; genutzt in Identifier-/String-Pfaden.
- **Grid-Track-Maximierung** (css-grid §12.5): `minmax(0,1220px)`-Tracks blieben
  0px→1px; Tracks werden jetzt gleichmäßig an endliche Größen maximiert (Einfrieren am
  Limit) und fr-Tracks bekommen `max(base, fr-Anteil)`. Seitenhöhe 43.850px→8.179px.
- **Anker-URL-Properties**: `n.pathname.charAt(0)` brach; `<a>`/`<area>` bekommen
  `href` (aufgelöst) sowie `protocol/host/hostname/port/pathname/search/hash/origin`
  via `GraalPageRuntime.locationParts`.
- **`globalThis.crypto`** fehlte komplett: Bootstrap mit `getRandomValues`,
  `randomUUID`, `subtle.digest` (SHA-1/256/384/512 über `__browicySubtleDigest`);
  Digest-Ergebnis als `ProxyArray` (unsigned).
- **`URL.createObjectURL`** (Worker-Feature-Detection) mit `blob:browicy-N`.
- **JS-injizierte `<style>`-Tags** (vue-style-loader `appendChild(createTextNode)`):
  `StyleSheetRegistry.updateStyleSheet(ownerNode, css)`; Hooks in `JsElement.putMember`
  und allen Kind-Mutationen. Live: 114 Stylesheets / 3634 Regeln statt 1/2307.
- **`RenderLayoutMetrics` nie verdrahtet** (offsetWidth/getBoundingClientRect=0 → Slick
  setzte Slides auf width:0): neues `LayoutMetricsAccessFactory`, verdrahtet in
  `BrowserInspector` (CLI-Viewport) und `BrowserFrame` (Panelgröße).
- **Statement-Limit 10M→100M**: Nuxt+Tracker überschritten 10M Statements.
- **Degenerierter `LinearGradientPaint`** (Start==End bei 0-Größe): `half<=0`→return,
  try/catch mit Endfarben-Füllung.
- **Float-Shrink-Wrap**: Prozent-Breiten trugen zur max-content-Breite bei (Slick-Slides
  mit `float:left` stapelten vertikal); `contentBased` durch
  `intrinsicWidths/intrinsicBoxWidth/intrinsicNodeWidth` ersetzt — nur
  `shrinkToFitWidth` nutzt `true`, alle anderen Aufrufer `false`; Float-Branch wieder
  auf `blockArea.width()`.
- **Placeholder-Rendering**: `RenderTreeBuilder.addInputText` rendert den Placeholder
  (Default `#767676`), Vendor-Pseudos `-webkit-input-placeholder`/`-moz-placeholder`
  werden zu `placeholder` normalisiert (Parser + `PseudoElementSupport` +
  `CompoundSelector`).
- **BFC-Overflow enthält Floats**: `overflow:hidden`-Boxen (Slick-Track) schließen
  Floats in ihre Höhe ein (CSS 2.1 §9.4.7) statt sie überlaufen zu lassen.
- **LayoutComparator**: Root-`html`-Box vom Vergleich ausgeschlossen — beide
  Extractoren liefern dort Artefakte (Chrome-Rect vs. synthetisierte Layout-Höhe).

Verifikation: `mvn install` grün (inkl. neuer `FloatConformanceTest` mit
Chromium-Vergleich für Slick-Float-Slides und Prozent-Kind im Float,
`PlaceholderRenderingTest`, Selector-Parser-Tests, `BrowicyEngineTest`-Reparatur aus
Commit 9b69d07). End-to-End: wetter.de rendert komplett — Header (Logo, Suche mit
Placeholder, Berlin/Köln-Pills, 18°), Hero-Slider mit echten API-Daten
(„Es bleibt trocken…", Heute 36°/17°, Sonntag 26°/17°, Montag 22°/15°), Regenradar mit
Kartenbild. Verbleibende Differenzen zu Chromium: Font-Zeilenhöhen (~76px Hero-Höhe),
Mapbox-Karteninteraktion (WebGL) mit Fallback-Meldung.

## sparkasse.de-Rendering (2026-08-15)

Entwicklungsziel: `www.sparkasse.de` (Next.js + MUI, CSS-in-JS) korrekt darstellen.
Referenz-Rendering via Chromium, Vergleich über `browicy-inspect.jar`. Gefunden und
behoben:

- **`display: contents`** erzeugte fälschlich eine Block-Box; Kinder nehmen jetzt
  direkt am umgebenden Block-/Flex-/Grid-Layout teil (Positioniertes `contents` wird
  zu `block`). Neues `RenderStyle.Display.CONTENTS`.
- **Grid-Platzierung**: unbekannte benannte Areas wurden fallengelassen (Item samt
  Inhalt verschwand). Jetzt Auto-Placement-Fallback. Neu: Auflösung benannter Linien
  (`grid-area: 1 / mediaLeft / auto / mediaRight`) gegen `grid-template-areas`
  (Bereichsname → Start-/Endlinie, implizite `-start`/`-end`-Linien), negative
  Liniennummern, `span` in `GridLine`-Record. `grid-area` wird im Parser in die vier
  Langformen expandiert.
- **Grid-Spuren**: `minmax(100%, 1600px)` behandelte den Prozent-Minwert als px;
  Prozentwerte werden jetzt gegen die Containerbreite aufgelöst. Gestreckte Items
  (align-items: stretch) erzwingen die Zellhöhe wie im Flex-Layout.
- **`visibility`** ist vererbbar; Kinder von `visibility:hidden`-Boxen malen nicht
  mehr (Text-/Bild-Fragmente prüfen `visible`).
- **Paint-Reihenfolge (z-index)**: positionierte Boxen mit `z-index:0` (MUI-Scrims)
  übermalten Flex-/Grid-Items mit `z-index>0`. Flex-/Grid-Item-Fragmente werden jetzt
  nach z-index gruppiert, negative positionierte zuerst, z>0 zuletzt.
- **Transiente HTTP-Fehler**: Script-/Style-/Bild-Loads wiederholen 429/5xx mit
  Backoff (WAF-Rate-Limiting von sparkasse.de lieferte zwischenzeitlich 503) und teilen
  sich den Fetch-Permit-Pool (begrenzte Parallelität).
- **data:-URIs** für `<img>` werden dekodiert (base64 oder roh/URL-kodiert) statt
  verworfen; SVG-Inhalte rendern über den bestehenden SVG-Pfad.

Verifikation: `mvn verify` grün; neue Regressionstests in `GridConformanceTest`
(Chromium-Vergleich für benannte Linien, `minmax(100%,…)`, `display:contents`-Items),
`DomViewPanelTest` (z-Reihenfolge, negative z, `visibility`-Vererbung),
`BrowicyEngineTest` (data:-URI-Loads) und `DocumentResourceScannerTest`. End-to-End:
lokale Fixture mit dem Sparkasse-Hero-Muster (Grid + contents + Scrim + data:-SVG)
rendert korrekt über den Inspector.

## AP-G1: Modulare JS-Proxy-Architektur & Handler-Aufteilung (2026-08-15)

Refactoring der monolithischen `switch(key)`-Blöcke in `JsElement`/`JsDocument`
zugunsten einer delegierenden Handler-Pipeline (Grundlage für alle weiteren
JS-Erweiterungen, z. B. Storage, DOMParser, Mutation-APIs):

- Neues Package `com.browicy.engine.js.handlers` mit dem zentralen
  `JsMemberHandler`-Interface (`canHandle`/`get`/`set` + `keys()`-Vertrag,
  gemeinsame Static-Helper wie `expectNode`, `toText`, `tag`).
- Spezialisierte Handler: `JsFormHandler` (value, checked, disabled, form,
  elements, selectedIndex, options, add/remove), `JsTableHandler` (rows, cells,
  caption, tHead/tFoot, insertRow/deleteCell, createCaption…), `JsUrlHandler`
  (href, protocol, host, hostname, port, pathname, search, hash, origin, src),
  `JsGeometryHandler` (getBoundingClientRect, getClientRects, offset*/client*).
- Weitere Element-Handler für Attribute/Inhalte (`JsAttributeHandler`),
  Node-Identität & Traversierung (`JsNodeHandler`), Selektoren
  (`JsQueryHandler`), Kind-Mutationen (`JsMutationHandler`), eingebettete Inhalte
  (`JsEmbeddedContentHandler`), Interaktion/Medien (`JsInteractiveHandler`) und
  Event-API (`JsEventHandler`).
- Document-Pipeline: `JsDocumentTraversalHandler` + `JsDocumentCreationHandler`
  (createElement, createRange, createNodeIterator, write, …).
- `JsElement` ist jetzt ein kompakter Dispatcher (133 Zeilen statt 736):
  geordnete Handler-Liste → Node-Konstanten → Expando-Fallback; `hasMember`/
  `getMemberKeys` leiten sich aus den `keys()`-Mengen der Handler ab.
- Ergänzt: `disabled`-Reflexion als neues Formular-Member; Node-Konstanten
  zentral in `JsNodeConstants` (statt dupliziert in beiden Proxies).

Verifikation: `mvn test` grün über alle 13 Module (engine-js: 139 Tests, davon
67 in `JavaScriptEngineTest` inkl. Vue-3-Mount unverändert grün — keine
Regression bei DOM-Zugriffen oder Expando-Eigenschaften).
