# browicy

Ein Browser mit eigener Engine — reines Java (21+), gebaut mit Maven.

## Module

* **[engine](./engine)** — die Browicy-Browser-Engine: HTML-Tokenizer, Parser und DOM.
  Keine externen Abhängigkeiten.
* **[desktop](./desktop)** — das Browser-Fenster (Swing): rahmenloses Fenster mit eigener
  Titelleiste, Tabs, Adressleiste und DOM-Renderer. Reines Java ohne UI-Fremdbibliotheken,
  damit später eine Kompilierung mit GraalVM native-image möglich ist.

## Bauen und Starten

```bash
# Alles bauen und Tests ausführen
mvn verify

# Browser starten
mvn -pl desktop -am compile exec:java

# Ausführbares Jar bauen und starten
mvn package
java -jar desktop/target/browicy-desktop-0.1.0-SNAPSHOT.jar
```

## GraalVM native-image (später)

Das `native`-Profil ist vorbereitet. Mit einem GraalVM-JDK (inkl. `native-image`) als `JAVA_HOME`:

```bash
mvn -Pnative -pl desktop -am package
```

Hinweis: Swing/AWT-Unterstützung in native-image erfordert ein aktuelles GraalVM;
ggf. sind zusätzliche Reachability-Metadaten nötig.

## Tests

```bash
mvn test
```
