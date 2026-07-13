# browicy

Ein Browser mit eigener Engine — reines Java (21+), gebaut mit Maven und GraalVM.

## Module

Die Engine ist in fachlich getrennte Maven-Module zerlegt. Das hält Abhängigkeiten
sichtbar und verhindert, dass HTML-, CSS-, JavaScript-, Netzwerk- und Rendering-Code
mit wachsender Feature-Menge wieder zu einem Monolithen zusammenwachsen.

* **[engine-dom](./engine-dom)** — DOM-Knoten, Events, Range und gemeinsame DOM-Verträge.
* **[engine-css](./engine-css)** — CSS-Parser, Selektoren, Kaskade, Spezifität und CSS-Werte.
* **[engine-html](./engine-html)** — HTML-Entities, Tokenizer und Tree-Construction; wendet
  nach dem Parsen die CSS-Kaskade auf das erzeugte DOM an.
* **[engine-js](./engine-js)** — GraalJS-Laufzeit und JavaScript-Bindings für das DOM.
* **[engine-net](./engine-net)** — HTTP-Client, Page-Loader und Netzwerkbeobachtung.
* **[engine-render](./engine-render)** — Render-Tree, Render-Styles sowie Block-/Inline-Boxen.
* **[engine](./engine)** — kompatible `browicy-engine`-Fassade, die die Teilmodule bündelt
  und mit `BrowicyEngine` den vollständigen Ladeablauf orchestriert.
* **[engine-integration-tests](./engine-integration-tests)** — modulübergreifende
  Integrationstests ohne zyklische Test-Abhängigkeiten zwischen den Produktivmodulen.
* **[devtools](./devtools)** — Entwicklerwerkzeuge; hängt nur vom Netzwerkmodul ab.
* **[desktop](./desktop)** — Swing-Oberfläche und Graphics2D-Renderer.

Die Abhängigkeitsrichtung ist bewusst einseitig:

```text
engine-dom <- engine-render <- engine-css <- engine-html <- engine-js
     ^                          ^
     +--------------------------+

engine-net ------------------------------------------> engine (Fassade)
engine-dom/render/css/html/js -----------------------> engine (Fassade)
```

Neue Features sollten im kleinsten passenden Modul landen. Das Fassade-Modul dient
der Komposition und Rückwärtskompatibilität, nicht als Ablage für fachliche Klassen.

## Bauen und Starten

Unter Windows verwendet das Projekt Oracle GraalVM `25.1.3+9.1` (JDK `25.0.3`).
Die Distribution liegt unter `D:\Graal\graalvm-25.1.3+9.1`. `mvn-graal.cmd` setzt
`JAVA_HOME` und `PATH` nur für den jeweiligen Aufruf; die systemweite Java-Konfiguration
bleibt unverändert.

```bat
# Alles bauen und Tests ausführen
mvn-graal.cmd verify

# Browser starten
run.cmd

# Ausführbares Jar bauen und mit GraalVM starten
mvn-graal.cmd package
D:\Graal\graalvm-25.1.3+9.1\bin\java.exe --sun-misc-unsafe-memory-access=allow -jar desktop\target\browicy-desktop-0.1.0-SNAPSHOT.jar
```

Das Flag `--sun-misc-unsafe-memory-access=allow` unterdrückt die JDK-Warnung,
die Truffle (GraalJS) beim Initialisieren auslöst; der nötige Native-Access ist
bereits im Jar-Manifest freigeschaltet (`Enable-Native-Access: ALL-UNNAMED`).
Bei `run.cmd` setzt `mvn-graal.cmd` beide Flags automatisch über `MAVEN_OPTS`.

Weitere Maven-Argumente werden unverändert durchgereicht, beispielsweise
`mvn-graal.cmd test`. In IntelliJ IDEA sollte für das Projekt und den Maven Runner
ebenfalls `D:\Graal\graalvm-25.1.3+9.1` als JDK ausgewählt werden. Zum Starten aus
der IDE die mitgelieferte Run-Konfiguration **„Browicy“** (`.run/Browicy.run.xml`)
verwenden — sie setzt die JVM-Flags, ohne die das JDK beim Initialisieren der
JavaScript-Engine (Truffle/GraalJS) Warnungen ausgibt.

## GraalVM native-image

Das `native`-Profil ist vorbereitet. Die installierte Distribution enthält `native-image`:

```bat
mvn-graal.cmd -Pnative -pl desktop -am package
```

Hinweis: Swing/AWT-Unterstützung in native-image erfordert ein aktuelles GraalVM;
ggf. sind zusätzliche Reachability-Metadaten nötig.

## JavaScript (Prototyp)

Die Engine führt beim Laden einer Seite alle Inline-`<script>`-Blöcke über
GraalJS aus (`com.browicy.engine.js.JavaScriptEngine`). Die Skripte laufen in
einer Sandbox ohne Host-Zugriff (kein Java, kein Dateisystem, keine Prozesse)
und mit Statement-Limit gegen Endlosschleifen. Als DOM-API stehen aktuell u.a.
`document.title`, `document.getElementById`, `document.createElement`,
`element.textContent`, `element.setAttribute` und `element.appendChild` zur
Verfügung; `console.log`-Ausgaben und Skriptfehler werden gesammelt
(`JsExecutionResult`) und sind wie im Browser nicht fatal.

Noch nicht unterstützt: externe Skripte (`<script src=…>`), Events, Timer
(`setTimeout`) und dynamisches Nachladen.

## Tests

```bat
mvn-graal.cmd test
```

Die 100 Acid3-Untertests liegen in einem separaten Opt-in-Modul, da noch nicht
unterstuetzte Browser-APIs dort erwartungsgemaess zu fehlschlagenden Tests fuehren:

```bat
mvn-graal.cmd -Pacid3 -pl acid3-tests -am test
```

Details zur JUnit-Abbildung und zur eingebundenen Original-Testseite stehen in
[`acid3-tests/README.md`](./acid3-tests/README.md).
