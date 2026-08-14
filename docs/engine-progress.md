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
