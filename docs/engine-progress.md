# Browser-engine progress workflow

`browser-cli` is the automation boundary for Browicy. It loads through `BrowicyEngine`,
waits for resources and the JavaScript event loop, builds the real render tree, and writes
one JSON report. It does not maintain a second test-only rendering path.

## Reproduce the CSS 2007 baseline

```bat
inspect.cmd "https://css3test.com/?filter=css2007" target\css3test-css2007.json
```

Baseline on 2026-07-13:

* css3test executes and reports **3%**.
* 400 DOM elements and 97 list/result items are created.
* The engine accepts 26 rules from the site's stylesheet.
* The only recorded script error is currently the optional Carbon Ads script calling the
  unsupported Fetch API. The css3test application itself completes.

The external site and its tests can change, so treat these numbers as a regression baseline,
not as a permanent expected value.

## Agent/CI loop

1. Run the inspector before a change and retain its JSON report.
2. Implement one capability in the smallest engine module.
3. Add a local deterministic regression test for that capability.
4. Run `mvn-graal.cmd verify`.
5. Run the same inspection again and compare `page.detectedScore`, `dom`, `css`,
   `renderTree`, `javascript.errors`, and failed `network` events.

JavaScript errors include source URL, line, and column. This is usually the most direct next
work item. Capability tests must stay conservative: `CSS.supports` reports only syntax/value
pairs that the engine actually accepts, so the external score cannot claim unimplemented CSS.
