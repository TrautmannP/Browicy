# Runbook: W3C CSS2.1-Harness (Chrome vs. Browicy)

Agenten-Anleitung für die Arbeit mit `w3c-css21-tests`. Ziel des Harness:
**Chrome rendert die W3C-CSS2.1-Tests als Referenz („so soll es aussehen"),
Browicy rendert denselben Test, ein Pixelvergleich liefert PASS/DIFF** –
Test-Driven Development für die Engine. Details und Grenzen: `README.md`
im selben Ordner, Provenienz der Suite: `UPSTREAM.md`. Wer die Suite
testgetrieben an der Engine abarbeiten soll, bekommt
[`AGENT-PROMPT.md`](AGENT-PROMPT.md) als Auftrag.

## Ablauf in Kürze

1. Chromium einmalig installieren (siehe unten).
2. Testmenge wählen (`-Dbrowicy.tests='<regex>'` – Kapitel oder eine Datei).
3. Harness laufen lassen: `mvn -Pw3c-css21 -pl w3c-css21-tests -am test ...`
   – fehlende Chrome-Referenzen werden automatisch erzeugt.
4. `w3c-css21-tests/target/w3c-css21/latest.html` öffnen: Status, Metriken,
   Chrome/Browicy/Diff-Bild je Test.
5. Bei DIFF: Ursache bestimmen (siehe „Interpretation"), Engine fixen,
   Lauf wiederholen.

## Setup (einmalig)

```bash
# Chromium für Playwright (lädt ~160 MB nach %LOCALAPPDATA%\ms-playwright)
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
# Einzelner Test – schnellster TDD-Loop (Name = relativer Pfad in der Suite)
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  "-Dbrowicy.tests=abspos/abspos-containing-block-initial-001\.xht"

# Ein ganzes Kapitel
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  -Dbrowicy.tests='abspos/.*'

# Baseline (Chrome-Referenzen) neu erzeugen – NUR wenn sich die Erwartung
# absichtlich ändern soll, nicht um Tests grün zu machen
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  -Dbrowicy.refreshReferences=true -Dbrowicy.tests='abspos/.*'

# Report-Modus: voller Lauf, JUnit bricht nicht bei DIFF ab
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  -Dmaven.test.failure.ignore=true -Dbrowicy.tests='abspos/.*'

# Font-Rauschen tolerieren (z. B. Textzeilen/AA-Kanten)
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  -Dbrowicy.tests='backgrounds/.*' -Dbrowicy.passRatio=0.03
```

Tipp: Auf der Windows-Konsole den Regex in doppelte Anführungszeichen
setzen (`"..."`), damit `\.` und `.*` nicht von der Shell gefressen werden.

## Steuerung (System-Properties)

| Property | Wirkung | Standard |
|---|---|---|
| `browicy.tests` | Regex über den relativen Testpfad; Filter | `.*` (alle ~9 800) |
| `browicy.refreshReferences` | Chrome-Referenzen überschreiben | `false` |
| `browicy.viewport` | Viewport beider Browser | `800x600` |
| `browicy.passRatio` | max. Diff-Quote für PASS | `0.0` |
| `browicy.outputDir` | Berichte/Artefakte | `target/w3c-css21` |

## Artefakte (unter `target/w3c-css21/`)

| Pfad | Inhalt |
|---|---|
| `latest.html` | Triage-Seite: Status + Chrome/Browicy/Diff je Test |
| `latest.json` | maschinenlesbarer Report (Status, Metriken, Meldung) |
| `references/<test>.png` | Chrome-Sollbild (die Erwartung) |
| `comparisons/<test>/{chrome,browicy,diff}.png` | Paar + Diff (magenta = abweichend) |

## Status interpretieren

- **PASS** – pixelidentisch (bzw. ≤ `passRatio`). Grün ist der Erfolg.
- **DIFF** – Abweichung. Diff-Bild + Metriken (`diffRatio`, `meanAbsDiff`,
  `maxAbsDiff`) im Report ansehen.
- **ERROR** – Chrome oder Browicy hat den Test nicht gerendert; Meldung im
  Report. Startet der Lauf gar nicht, schlägt der synthetische Test
  `<harness>` fehl (z. B. Suite-Download oder Chromium fehlt) – dessen
  Meldung zuerst lesen.
- **SKIP** – auf `src/main/resources/w3c-css21/skip.txt` (Regex pro Zeile,
  `#`-Kommentare). Für interaktive Tests (`:hover`/`:active`/cursor) etc.

## DIFF-Ursachen unterscheiden (wichtig – nicht alles ist ein Engine-Bug)

1. **Text-Glyphen:** kleine `diffRatio` (1–3 %), nur Textzeilen magenta →
   Ahem-Schrift fehlt oder Font-Parität. Kein Engine-Fehler; mit Ahem
   nachmessen. `passRatio` darf hier als bewusste Toleranz dienen.
2. **1-px-Kanten/Antialiasing:** Rechteckränder magenta, Mittelteil gleich →
   Rasterungs-Differenz; `maxAbsDiff` klein (1–3).
3. **Echter Layout-/Paint-Unterschied:** Flächen fehlen/verschoben, alles
   magenta, `diffRatio` groß → Engine-Gap. **Dann:**
   - Ursache konkret benennen (welche Box, welche Position, welcher Wert),
   - Eintrag in `engine-css/CSS2-TODO.md` (Kapitel-Verweis) ergänzen,
   - Engine fixen (Module `engine-*`), `mvn -q install -DskipTests`,
   - Harness-Lauf wiederholen, bis PASS.

## TDD-Zyklus (so treibt man ein Engine-Feature)

1. Testmenge auf das Feature eingrenzen (`browicy.tests`), Referenz wird
   beim ersten Lauf automatisch erzeugt.
2. Rot: DIFF → `diff.png` + `chrome.png` ansehen, konkrete Abweichung
   benennen (Box/Position/Wert).
3. Engine ändern, neu bauen, Lauf wiederholen → grün.
4. `refreshReferences=true` nur zur bewussten Neu-Baseline (z. B. neue
   Chrome-Version), nie um Tests zu „reparieren".

## Gotchas (aus der Praxis)

- **Erster Lauf braucht Netzwerk** (lädt die Suite, ~52 MB Zip) und
  Chromium. Suite-Cache: `~/.browicy/w3c-css21-suite/<sha>/`; überlebt
  `mvn clean`; bei Problemen Ordner löschen → nächster Lauf lädt neu.
- **Suite-Pin:** `Css21Suite.UPSTREAM_SHA`; wechseln = neuer Cache-Ordner,
  `UPSTREAM.md` prüfen.
- **JVM-Font-Varianz:** Referenz und Browicy-Bild stammen aus verschiedenen
  JVM-Läufen; minimal unterschiedliche Glyphen möglich. Baseline bei Bedarf
  neu erzeugen.
- **`.xht`-Parsing:** Browicy parst XHTML mit dem HTML-Parser, Chrome als
  XML (`application/xhtml+xml`). Seltene Parser-Differenzen möglich.
- **Viewport-only:** Verglichen wird die `800x600`-Fläche; Inhalte darunter
  fallen bei beiden Seiten gleich weg.
- **Nicht verwechseln:** `engine-integration-tests/com.browicy.conformance`
  ist die Layout-Box-Harness (getBoundingClientRect-Vergleich), dieses Modul
  der Pixel-Harness. Beide sind Chrome-gestützt und laufen nicht im
  Standard-Testlauf.

## Bekannte Engine-Lücken (erste Funde, 2026-08-15)

- `abspos/abspos-containing-block-initial-*` – Initial-Containing-Block /
  Viewport-Positionierung bei `scrollTo` (CSS2 §10.1)
- `floats/floats-rule3-outside-*` / `floats/floats-rule7-outside-*` – Float-Regel-3-
  Interaktion bei zwei Floats im selben BFC (§9.5.1); Regel-3-Fix umgesetzt (Stand
  siehe `engine-css/CSS2-TODO.md` Kap. 9), Rest-Diff = UA-body-Margin-Artefakt (8 px)
- `floats/floats-wrap-*`, `floats/floats-placement-vertical-*`,
  `floats/floats-zero-height-wrap-*` – weitere Float-Platzierungs-/Wrap-Lücken (§9.5.1)
- `normal-flow/block-in-inline-margins-001a` – Border leerer
  Block-in-Inline-Boxen wird gefüllt statt als Rahmenring gemalt
