# browicy

Ein Browser mit eigener Engine — reines Java (21+), gebaut mit Maven und GraalVM.

## Module

* **[engine](./engine)** — die Browicy-Browser-Engine: HTML-Tokenizer, Parser, DOM,
  eigener HTTP-Client sowie JavaScript-Ausführung. HTML/DOM/Netzwerk sind eigenständig
  implementiert; für JavaScript wird [GraalJS](https://www.graalvm.org/javascript/)
  über die GraalVM-Polyglot-API eingebettet (einzige externe Abhängigkeit).
* **[desktop](./desktop)** — das Browser-Fenster (Swing): rahmenloses Fenster mit eigener
  Titelleiste, Tabs, Adressleiste und DOM-Renderer. Reines Java ohne UI-Fremdbibliotheken,
  damit später eine Kompilierung mit GraalVM native-image möglich ist.

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
