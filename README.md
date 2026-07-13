# browicy

A browser with its own engine — pure Java (21+), built with Maven and GraalVM.

## Modules

The engine is split into functionally separated Maven modules. This keeps dependencies
visible and prevents HTML, CSS, JavaScript, network, and rendering code from growing
back into a monolith as the feature set expands.

* **[engine-selectors](./engine-selectors)** — DOM-independent selector parser, AST, matching, and specificity.
* **[engine-dom](./engine-dom)** — DOM nodes, events, ranges, and shared DOM contracts.
* **[engine-css](./engine-css)** — stylesheet/declaration parser, cascade, and CSS values.
* **[engine-html](./engine-html)** — HTML entities, tokenizer, and tree construction; after
  parsing it applies the CSS cascade to the generated DOM.
* **[engine-js](./engine-js)** — GraalJS runtime and JavaScript bindings for the DOM.
* **[engine-net](./engine-net)** — HTTP client, page loader, and network observation.
* **[engine-render](./engine-render)** — render tree, render styles, and block/inline boxes.
* **[engine](./engine)** — compatible `browicy-engine` facade that bundles the submodules
  and, with `BrowicyEngine`, orchestrates the full loading process.
* **[engine-integration-tests](./engine-integration-tests)** — cross-module
  integration tests without cyclic test dependencies between the production modules.
* **[devtools](./devtools)** — developer tools; depends only on the network module.
* **[desktop](./desktop)** — Swing interface and Graphics2D renderer.

The dependency direction is intentionally one-way:

```text
engine-selectors <- engine-dom <- engine-render <- engine-css <- engine-html <- engine-js
       ^                                      |
       +--------------------------------------+

engine-net ---------------------------------------------------------> engine (facade)
engine-selectors/dom/render/css/html/js ----------------------------> engine (facade)
```

New features should land in the smallest suitable module. The facade module serves
composition and backward compatibility, not as a dumping ground for domain classes.

## Building and Running

On Windows the project uses Oracle GraalVM `25.1.3+9.1` (JDK `25.0.3`).
The distribution is located at `D:\Graal\graalvm-25.1.3+9.1`. `mvn-graal.cmd` sets
`JAVA_HOME` and `PATH` only for the respective invocation; the system-wide Java configuration
remains unchanged.

```bat
# Build everything and run tests
mvn-graal.cmd verify

# Start the browser
run.cmd

# Build an executable jar and start it with GraalVM
mvn-graal.cmd package
D:\Graal\graalvm-25.1.3+9.1\bin\java.exe --sun-misc-unsafe-memory-access=allow -jar desktop\target\browicy-desktop-0.1.0-SNAPSHOT.jar
```

The `--sun-misc-unsafe-memory-access=allow` flag suppresses the JDK warning
that Truffle (GraalJS) triggers during initialization; the required native access is
already enabled in the jar manifest (`Enable-Native-Access: ALL-UNNAMED`).
For `run.cmd`, `mvn-graal.cmd` sets both flags automatically via `MAVEN_OPTS`.

Additional Maven arguments are passed through unchanged, for example
`mvn-graal.cmd test`. In IntelliJ IDEA, `D:\Graal\graalvm-25.1.3+9.1` should also
be selected as the JDK for the project and the Maven runner. To start from
the IDE, use the bundled run configuration **"Browicy"** (`.run/Browicy.run.xml`)
— it sets the JVM flags without which the JDK emits warnings when initializing the
JavaScript engine (Truffle/GraalJS).

## GraalVM native-image

The `native` profile is prepared. The installed distribution includes `native-image`:

```bat
mvn-graal.cmd -Pnative -pl desktop -am package
```

Note: Swing/AWT support in native-image requires a recent GraalVM;
additional reachability metadata may be needed.

## JavaScript (Prototype)

When loading a page, the engine executes all inline `<script>` blocks via
GraalJS (`com.browicy.engine.js.JavaScriptEngine`). The scripts run in
a sandbox without host access (no Java, no file system, no processes)
and with a statement limit to guard against infinite loops. The currently
available DOM API includes, among others,
`document.title`, `document.getElementById`, `document.querySelector`,
`document.createElement`, `element.classList`, `element.textContent`,
`element.setAttribute`, and `element.appendChild`; `console.log` output and
script errors are collected (`JsExecutionResult`) and, as in a browser, are not fatal.

Not yet supported: external scripts (`<script src=…>`), events, timers
(`setTimeout`), and dynamic loading.

## Tests

```bat
mvn-graal.cmd test
```

The 100 Acid3 subtests are located in a separate opt-in module, since not yet
supported browser APIs cause the expected test failures there:

```bat
mvn-graal.cmd -Pacid3 -pl acid3-tests -am test
```

Details on the JUnit mapping and the embedded original test page are in
[`acid3-tests/README.md`](./acid3-tests/README.md).
