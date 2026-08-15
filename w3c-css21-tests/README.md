# W3C CSS 2.1 Test Suite – Chrome-vs-Browicy Harness

Dieses optionale Modul nutzt die [W3C CSS 2.1 Conformance Test
Suite](https://www.w3.org/Style/CSS/Test/) (Quelle und Pin: siehe
[`UPSTREAM.md`](UPSTREAM.md)) und vergleicht die Darstellung jedes Tests
pixelweise zwischen **headless Chromium (Playwright)** und **Browicy**:

> Chrome zeigt, wie es richtig aussehen soll. Browicy wird gegen diese
> Referenz getestet – Test-Driven Development für die Engine.

Die Suite (55 MB, 13 401 Dateien) ist **nicht eingecheckt**: Der Harness
lädt sie beim ersten Lauf von der gepinnten Upstream-Revision
(`w3c/csswg-test@8eced53…`) und cached sie unter
`~/.browicy/w3c-css21-suite/<sha>/` – danach läuft alles offline.

## Voraussetzungen

1. Playwright-Chromium einmalig installieren (lädt ~160 MB nach
   `%LOCALAPPDATA%\ms-playwright`):

   ```bash
   mvn -pl desktop exec:java \
     -Dexec.mainClass=com.microsoft.playwright.CLI \
     -Dexec.args="install chromium"
   ```

2. Für aussagekräftige Schrift-Tests die **Ahem-Schrift** installieren
   (https://www.w3.org/Style/CSS/Test/Fonts/Ahem/). Viele Suite-Tests
   rendern Text nur mit Ahem deterministisch; ohne sie weichen Chrome und
   Browicy bei Schriftglyphen ab (kein Engine-Fehler). Die Tests werden
   nicht verändert – die Font-Wahl ist Teil der Testumgebung.

3. Netzwerk für den **ersten** Harness-Lauf (lädt die Testsuite als Zip,
   ~52 MB). Danach offline.

Operative Details für Agenten und CI: siehe [`RUNBOOK.md`](RUNBOOK.md).

## Laufvarianten

Standardlauf (baut abhängige Module mit, führt aber ohne das Profil nicht
aus – das Modul gehört nicht zum Standard-Reaktor):

```bash
# 1. Baseline erzeugen: Chrome rendert alle (gefilterten) Tests neu
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  -Dbrowicy.refreshReferences=true \
  -Dbrowicy.tests='abspos/.*'

# 2. TDD-Schleife: nur Browicy gegen gespeicherte Chrome-Referenzen
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  -Dbrowicy.tests='abspos/.*'

# Einzelner Test (schnellster Loop)
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  -Dbrowicy.tests='abspos/abspos-containing-block-initial-001.xht'

# Kompletter Lauf ohne Abbruch bei DIFF (Report-Modus)
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  -Dmaven.test.failure.ignore=true
```

## Steuerung

| System-Property | Bedeutung | Standard |
|---|---|---|
| `browicy.tests` | Regex über den relativen Testpfad | `.*` (alle ~9 800 Tests) |
| `browicy.refreshReferences` | Chrome-Referenzen neu erzeugen | `false` |
| `browicy.viewport` | Viewport für beide Browser | `800x600` |
| `browicy.passRatio` | max. Diff-Quote für PASS | `0.0` (pixelidentisch) |
| `browicy.outputDir` | Berichts-/Artefaktverzeichnis | `target/w3c-css21` |

Achtung: `mvn clean` löscht `target/w3c-css21` – fehlende Referenzen werden
beim nächsten Lauf automatisch neu erzeugt. Der Suite-Cache
(`~/.browicy/w3c-css21-suite/`) überlebt `mvn clean`.

## Status je Test

- **PASS** – Browicy-Darstellung pixelidentisch (bzw. ≤ `passRatio`) zur Chrome-Referenz
- **DIFF** – sichtbare Abweichung; Diff-Bild und Metriken im Report
- **ERROR** – Chrome oder Browicy konnte den Test nicht rendern (Meldung im Report)
- **SKIP** – auf `skip.txt` (interaktive/Nicht-Visual-Tests)

Fehlende Referenzen erzeugt der Harness automatisch beim ersten Lauf (Chrome
rendert sie). `-Dbrowicy.refreshReferences=true` überschreibt vorhandene
Referenzen – nötig, wenn sich die Erwartung ändern soll (neue Chrome-Version,
geänderte Baseline).

## Artefakte

`target/w3c-css21/`:

- `latest.html` – Triage-Seite mit Chrome/Browicy/Diff-Bildern je Test
- `latest.json` – maschinenlesbarer Report (Status + Metriken)
- `references/<test>.png` – Chrome-Sollbilder (die Test-Erwartung)
- `comparisons/<test>/{chrome,browicy,diff}.png` – Paar plus Diff je Test

## Grenzen

- Vergleich auf **Viewport-Ebene** (kein Full-Page-Rolling); beide Browser
  rendern dieselbe `800x600`-Fläche.
- Browicy parst `.xht`-Dateien mit seinem HTML-Parser, Chrome als XML
  (`application/xhtml+xml`). Inhaltlich identische Tests; Parser-Differenzen
  können einzelne DIFFs erklären.
- Schrift-Rendering ohne Ahem weicht ab (siehe oben). Zudem kann die
  Java2D-Schriftrasterung zwischen JVMs minimal variieren (Font-Cache):
  Referenzen und Browicy-Bilder stammen dann aus verschiedenen JVM-Läufen.
  Bei Bedarf Baseline mit `-Dbrowicy.refreshReferences=true` neu erzeugen.
- Der volle Lauf (~9 800 Tests) dauert je nach Rechner 1–3 Stunden –
  gezielte Teilmengen über `browicy.tests` sind der empfohlene Weg.
