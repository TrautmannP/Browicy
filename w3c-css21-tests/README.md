# W3C CSS 2.1 Test Suite – Chrome-vs-Browicy Harness

Dieses optionale Modul nutzt die [W3C CSS 2.1 Conformance Test
Suite](https://www.w3.org/Style/CSS/Test/) (Quelle und Pin: siehe
[`UPSTREAM.md`](UPSTREAM.md)) und vergleicht die Darstellung jedes Tests
pixelweise sowie auf **DOM-Box-Geometrie-Ebene** zwischen **headless Chromium (Playwright)** und **Browicy**:

> Chrome zeigt, wie es richtig aussehen soll. Browicy wird gegen diese
> Referenz getestet – Test-Driven Development für die Engine.

Die Suite (55 MB, 13 401 Dateien) ist **nicht eingecheckt**: Der Harness
lädt sie beim ersten Lauf von der gepinnten Upstream-Revision
(`w3c/csswg-test@8eced53…`) und cached sie unter
`~/.browicy/w3c-css21-suite/<sha>/` – danach läuft alles offline.

## Voraussetzungen

1. Playwright-Chromium einmalig installieren (lädt ~160 MB nach
   `%LOCALAPPDATA%\ms-playwright`; das `desktop`-Modul schaltet
   `exec.skip` frei, deshalb ohne zusätzliches Flag):

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

## Schnelles Layout-Debugging via CLI (`LayoutTreeDiffer`)

Um ohne kompletten Harness-Lauf die exakten Pixel- und Box-Modell-Deltas
zwischen Chrome und Browicy auf der Konsole auszugeben:

```bash
# Einmalig: Modul-Abhängigkeiten installieren (Standard-Build)
mvn -q install -DskipTests

# Layout-Tree-Diff eines Tests (headless Chromium + Browicy in-process;
# exec.skip=false ist nötig, weil der Parent-POM exec global sperrt;
# ohne -am, damit exec:java nur auf diesem Modul läuft)
mvn -Pw3c-css21 -pl w3c-css21-tests compile exec:java \
  -Dexec.mainClass=com.browicy.css21.LayoutTreeDiffer \
  -Dexec.skip=false \
  "-Dexec.args=floats/floats-rule7-outside-left-001.xht"
```

Ausgabebeispiel:
```text
Layout-Tree-Vergleich: 5 Elemente, 2 PASS, 3 DIFF, 0 Fehlt, 0 Extra (Max dPos: 0.0px, Max dSize: 0.0px)
------------------------------------------------------------------------------------------------------------------------
Status | Element (Pfad)                  | Chrome (x,y wxh)       | Browicy (x,y wxh)      | Delta (dx,dy dwxdh)
------------------------------------------------------------------------------------------------------------------------
PASS   | html > body:nth-of-type(1)      | (8.0, 8.0) 784.0x0.0   | (8.0, 8.0) 784.0x0.0   | (+0.0, +0.0) +0.0x+0.0
DIFF   | ...of-type(1) > div:nth-of-type(1) | (8.0, 8.0) 500.0x500.0 | (8.0, 8.0) 500.0x500.0 | (+0.0, +0.0) +0.0x+0.0
       -> Style-Diff [margin-right]: Chrome='0px', Browicy='284px'
------------------------------------------------------------------------------------------------------------------------
```

Verbleibende `DIFF`-Zeilen bei passenden Rects sind Reporting-Quirks der
`RenderLayoutMetrics` (positionsabgeleitete Used Values für `margin-left`/
`margin-right` auf Floats) — kein Layout-Effekt, siehe RUNBOOK.

Optionen: `--json` (strukturierte JSON-Ausgabe), `--out <datei>` (Ergebnis
zusätzlich in Datei schreiben). Exit-Codes: `0` = alle Boxen passen,
`1` = Layout-Diffs, `2` = Aufruf-/Laufzeitfehler. Der Browser wird am Ende
immer geschlossen (try-with-resources; Chromium läuft headless).

## Steuerung

| System-Property | Bedeutung | Standard |
|---|---|---|
| `browicy.tests` | Regex über den relativen Testpfad | `.*` (alle ~9 800 Tests) |
| `browicy.refreshReferences` | Chrome-Referenzen neu erzeugen | `false` |
| `browicy.viewport` | Viewport für beide Browser | `800x600` |
| `browicy.passRatio` | max. Diff-Quote für PASS | `0.0` (pixelidentisch) |
| `browicy.outputDir` | Berichts-/Artefaktverzeichnis | `target/w3c-css21` |

## Artefakte

`target/w3c-css21/`:

- `latest.html` – Triage-Seite mit Chrome/Browicy/Diff-Bildern und Links zu den Layout-Diffs je Test
- `latest.json` – maschinenlesbarer Report (Status + Pixel- & Layout-Metriken)
- `references/<test>.png` – Chrome-Sollbilder (die Test-Erwartung)
- `comparisons/<test>/{chrome,browicy,diff}.png` – Paar plus Diff je Test
- `comparisons/<test>/layout-diff.txt` – Tabellarischer Bounding-Box-Vergleich
- `comparisons/<test>/layout-diff.json` – Strukturierte JSON-Element-Geometriedaten
