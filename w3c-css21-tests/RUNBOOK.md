# Runbook: W3C CSS2.1-Harness (Chrome vs. Browicy)

Agenten-Anleitung für die Arbeit mit `w3c-css21-tests`. Ziel des Harness:
**Chrome rendert die W3C-CSS2.1-Tests als Referenz („so soll es aussehen"),
Browicy rendert denselben Test, ein Pixelvergleich sowie ein
DOM-Layout-Tree-Vergleich liefern PASS/DIFF** – Test-Driven Development für
die Engine.

## Ablauf in Kürze

1. Chromium einmalig installieren (siehe unten).
2. Testmenge wählen (`-Dbrowicy.tests='<regex>'` – Kapitel oder eine Datei).
3. Harness laufen lassen: `mvn -Pw3c-css21 -pl w3c-css21-tests -am test ...`
   – fehlende Chrome-Referenzen werden automatisch erzeugt.
4. `w3c-css21-tests/target/w3c-css21/latest.html` öffnen: Status, Metriken,
   Chrome/Browicy/Diff-Bilder sowie direkte Links zum `layout-diff.txt` je Test.
5. Bei DIFF: Ursache über den **Layout-Tree-Differ** (CLI oder Datei)
   analysieren, Engine in `engine-*` fixen, Lauf wiederholen.

## Setup (einmalig)

```bash
# Chromium für Playwright (lädt ~160 MB nach %LOCALAPPDATA%\ms-playwright;
# desktop-Modul schaltet exec.skip frei)
mvn -pl desktop exec:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install chromium"

# Optional, für aussagekräftige Schrift-Tests:
# Ahem-Schrift installieren (https://www.w3.org/Style/CSS/Test/Fonts/Ahem/)
```

## Kern-Befehle

Immer vom Repo-Root. `-am` baut die abhängigen Module mit (das Modul gehört
nicht zum Standard-Reaktor; nur mit `-Pw3c-css21` aktiv).

```bash
# 1. Einzelner Test – schnellster TDD-Loop
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  "-Dbrowicy.tests=abspos/abspos-containing-block-initial-001\.xht"

# 2. Gezielter CLI-Layout-Diff (Box-Positionen direkt im Terminal, headless;
#    Chromium wird am Ende immer geschlossen)
mvn -Pw3c-css21 -pl w3c-css21-tests compile exec:java \
  -Dexec.mainClass=com.browicy.css21.LayoutTreeDiffer \
  -Dexec.skip=false \
  "-Dexec.args=floats/floats-rule7-outside-left-001.xht"
#    Varianten: "--json" für strukturierte Ausgabe, "--out <datei>"
#    Exit-Codes: 0 = alle Boxen passen, 1 = Layout-Diffs, 2 = Fehler

# 3. Ein ganzes Kapitel testen
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  -Dbrowicy.tests='abspos/.*'

# 4. Report-Modus: voller Lauf, JUnit bricht nicht bei DIFF ab
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  -Dmaven.test.failure.ignore=true -Dbrowicy.tests='floats/.*'
```

**Wichtig zum CLI-Aufruf:** Der Parent-POM sperrt `exec:java` global
(`exec.skip=true`), nur `desktop` schaltet frei – deshalb `-Dexec.skip=false`.
Kein `-am` verwenden: sonst läuft `exec:java` auf jedem Reaktor-Modul und
scheitert am Root-POM ohne Classpath. Voraussetzung ist ein einmaliger
`mvn -q install -DskipTests` (Standard-Build installiert die Modul-
Abhängigkeiten; das Modul selbst gehört nicht zum Standard-Reaktor).

## Triage-Workflow bei DIFF

1. In `target/w3c-css21/latest.html` das abweichende Testbeispiel ansehen.
2. Den Link **`Layout-Diff (TXT)`** anklicken oder `LayoutTreeDiffer` in der
   Konsole aufrufen.
3. In der Tabelle die Zeile mit Status `DIFF`/`MISS`/`EXTRA` suchen:
   - Welches Element weicht ab? (Pfad, z. B. `…div.mid:nth-of-type(2)`)
   - Welche Achse ist falsch? (`dx`, `dy`, `dWidth`, `dHeight`)
   - Welche Styles weichen ab? (`-> Style-Diff [marginTop]: …`)
4. Den Fehler direkt im zuständigen Engine-Modul (`engine-render`,
   `engine-css`) beheben.
5. `mvn -q install -DskipTests` und Testlauf wiederholen, bis `PASS`.

### Interpretations-Hinweise (Erfahrungswerte)

- **`html > body` bei (8, 8)**: UA-`body`-Margin (Chrome wie Browicy: 8 px
  auf allen vier Seiten) — umgesetzt, kein Artefakt mehr; die floats-
  Familien (rule3/rule7/wrap-top-below) sind dadurch auf PASS bzw. maxΔPos 0.
  `floats-wrap-bfc-00{1,3,4,5}` sind PASS (Presentational Hints
  `table[width/height]`, 300px-Soll); Rest-Diffs: bfc-006 Caption-Layout,
  bfc-007 Margin-Kollaps, bfc-outside-001 textarea (replaced),
  placement-vertical (p-UA-Defaults) — je eigener Zyklus.
- **Style-Diff `margin-left`/`margin-right` auf Floats** (z. B. Chrome
  `0px` vs. Browicy `300px`): Browicys `RenderLayoutMetrics` meldet dort
  positionsabgeleitete Used Values statt Computed Values – bekanntes
  Reporting-Quirk, **kein Layout-Effekt** (die Rect-Spalten sind die
  Primärquelle).
- **`html`/`head` fehlen in der Tabelle**: beabsichtigt (Metadaten-Tags
  werden auf beiden Seiten übersprungen).
- **`display:none`-Nachfahren fehlen**: beabsichtigt (beide Seiten
  überspringen sie).
- Der **Pixel-Harness bleibt das PASS/DIFF-Gate**; der Layout-Tree-Differ
  dient der Lokalisierung. `layoutMismatches`/`maxLayoutPositionDelta` in
  `latest.json` sind Zusatzmetriken (kein Gate).
