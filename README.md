# Browicy

**Browicy** is an experimental desktop web browser and browser engine written in Java. It loads web pages, constructs a DOM, applies CSS, executes a sandboxed subset of JavaScript through GraalJS, and renders the resulting page with Swing and Java2D.

The project is a work in progress rather than a standards-compliant replacement for an established browser. Its purpose is to explore browser-engine architecture in a modular, inspectable Java codebase.

## Highlights

- HTML parsing, DOM construction, events, ranges, and CSS selector matching
- CSS parsing, cascade resolution, render-tree construction, and block/inline layout
- HTTP(S) page and subresource loading with resource-access policies
- Sandboxed JavaScript execution with selected DOM APIs, timers, microtasks, and module loading
- A Swing desktop interface backed by a Java2D renderer
- A headless CLI that produces machine-readable diagnostics for pages, DOM, CSS, rendering, JavaScript, and network activity

## Project structure

The Maven modules follow the engine's responsibilities and keep dependencies explicit:

- `engine-selectors`: CSS selector parsing, matching, and specificity
- `engine-dom`: DOM nodes, events, ranges, and shared DOM contracts
- `engine-css`: stylesheets, declarations, cascade resolution, and CSS values
- `engine-html`: entities, tokenization, tree construction, and style application
- `engine-js`: GraalJS integration and JavaScript-to-DOM bindings
- `engine-net`: HTTP client, document loading, and network observation
- `engine-render`: render tree, styles, layout boxes, and painting data
- `engine`: public facade that composes the engine modules
- `browser-cli`: headless page inspection and JSON reporting
- `devtools`: developer tooling built on network observation
- `desktop`: Swing user interface and Java2D renderer
- `engine-integration-tests`: cross-module integration tests

## Requirements

- Java 21 or newer
- Maven 3.9 or newer
- A GraalVM JDK is recommended for running pages that use JavaScript and for native-image builds

The build uses GraalJS version `25.1.3`. Use a compatible GraalVM distribution when running the desktop browser or inspector with JavaScript enabled.

## Build and run

Build the complete project and run its default test suite:

```bash
mvn verify
```

Start the desktop browser:

```bash
mvn -pl desktop -am compile exec:java
```

Create the executable desktop JAR:

```bash
mvn -pl desktop -am package
java -jar desktop/target/browicy-desktop-0.1.0-SNAPSHOT.jar
```

On JDK versions that require it, add `--sun-misc-unsafe-memory-access=allow` when launching the JAR. The manifest already enables the native access required by Truffle.

## Inspect pages without the UI

The headless inspector exercises the same engine as the desktop browser and emits a stable JSON report. It includes the final page state, DOM inventory, accepted CSS rules, render-tree counts, JavaScript errors with source locations, and network requests.

```bash
mvn -pl browser-cli -am package -DskipTests
java -jar browser-cli/target/browicy-inspect.jar "https://example.com" --output report.json
```

For visual regression tests, the inspector can capture the same Java2D rendering
as the desktop browser as a PNG. Viewport size and optionally the full document
height can be explicitly controlled:

```bash
java -jar browser-cli/target/browicy-inspect.jar "https://example.com" \
  --output report.json \
  --screenshot artifacts/example.png \
  --viewport 1280x720 \
  --full-page
```

The JSON report includes the absolute artifact path, image and viewport
dimensions, file size, and SHA-256 hash under `screenshot`. This allows the PNG
to be uniquely matched against a reference image and compared pixel by pixel
in a downstream step.

This output is suitable for automated regression checks and for investigating missing browser capabilities. See [the engine progress notes](docs/engine-progress.md) for an example workflow and compatibility baseline.

The inspector also writes compatibility findings to stderr and includes a deduplicated
`compatibility` section in schema version 2. It lists rejected CSS properties, values and
selectors, likely missing JavaScript browser APIs inferred from runtime errors, and HTML
elements that require specialized engine behavior. Each finding contains its occurrence
count plus a small set of sources and examples, so reports remain readable on large pages.

## JavaScript support

Inline and external scripts run in a sandbox without access to Java, the file system, or processes. The current API includes common capabilities such as DOM queries and mutation, `classList`, inline styles, `CSS.supports`, `URLSearchParams`, timers, microtasks, lifecycle events, and console/error collection.

Static ES modules with default imports can be loaded recursively over HTTP(S). Dynamic `import()`, named imports, Fetch/XHR, full CSSOM support, and many browser and layout APIs are not implemented yet.

## Testing and compatibility

Run the default tests with:

```bash
mvn test
```

The optional Acid3 module keeps known unsupported browser APIs out of the default suite:

```bash
mvn -Pacid3 -pl acid3-tests -am test
```

An optional compatibility-report profile runs the CSS3Test CSS-2007 filter and the embedded Acid3 harness, producing JSON and HTML reports under `target/compatibility-reports`:

```bash
mvn -Pcompatibility-report -pl acid3-tests -am verify
```

Expected conformance gaps are reported without failing this reporting build. The stricter Acid3 test command reports every failing subtest as a JUnit failure.

## Native image

The desktop module provides an experimental GraalVM native-image profile:

```bash
mvn -Pnative -pl desktop -am package
```

Swing/AWT native-image support depends on the GraalVM version and may require additional reachability metadata.
