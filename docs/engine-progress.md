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

Baseline recorded on 2026-07-14:

- CSS3Test CSS-2007 reports **33/94 passed (23%)**.
- The page contains 777 DOM nodes: 400 elements, 375 text nodes, and 97 result-list items.
- The engine loads two stylesheets and accepts 36 CSS rules.
- The render tree contains 554 nodes: 246 block boxes, 81 inline boxes, and 227 text runs.
- No JavaScript errors are recorded. The page's Carbon Ads request emits one CORS-related console message.

The test site and its content can change. Treat these values as a regression baseline, not
as permanent expected results.

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

The run on 2026-07-14 recorded 33/94 CSS3Test cases (23%) and 57/100 Acid3 subtests,
for 90/194 cases overall. Reports are written to `target/compatibility-reports`: `latest.html`
is intended for human review, while `latest.json` is intended for CI and automated analysis.
Timestamped copies are retained for trend tracking.
