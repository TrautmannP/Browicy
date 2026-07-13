# browicy

Ein Browser mit eigener Engine — reines Java (21+), gebaut mit Maven und GraalVM.

## Module

* **[engine](./engine)** — die Browicy-Browser-Engine: HTML-Tokenizer, Parser und DOM.
  Keine externen Abhängigkeiten.
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
D:\Graal\graalvm-25.1.3+9.1\bin\java.exe -jar desktop\target\browicy-desktop-0.1.0-SNAPSHOT.jar
```

Weitere Maven-Argumente werden unverändert durchgereicht, beispielsweise
`mvn-graal.cmd test`. In IntelliJ IDEA sollte für das Projekt und den Maven Runner
ebenfalls `D:\Graal\graalvm-25.1.3+9.1` als JDK ausgewählt werden.

## GraalVM native-image

Das `native`-Profil ist vorbereitet. Die installierte Distribution enthält `native-image`:

```bat
mvn-graal.cmd -Pnative -pl desktop -am package
```

Hinweis: Swing/AWT-Unterstützung in native-image erfordert ein aktuelles GraalVM;
ggf. sind zusätzliche Reachability-Metadaten nötig.

## Tests

```bat
mvn-graal.cmd test
```
