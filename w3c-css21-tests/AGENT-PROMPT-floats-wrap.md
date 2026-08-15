# Agenten-Auftrag: Float-Wrap-Familie `floats-wrap-top-below-*` (CSS2.1 §9.5.1)

> **Ziel:** Die Browicy-CSS-Engine gegen die W3C-CSS2.1-Tests
> `floats/floats-wrap-top-below-inline-00{2,3}{l,r}.xht` und
> `floats/floats-wrap-top-below-bfc-00{2,3}{l,r}.xht` abgleichen: Die
> **Zeilenboxen** (inline) bzw. **BFC-Wurzel-Blöcke** (bfc-Varianten) werden
> wie in Chrome unter/neben die Floats des BFC platziert, wenn ein Float
> unter einen anderen Float verschoben wurde. Rot → Triage → Fix in
> `engine-render` → grün(er) im Chrome-vs-Browicy-Harness (Chrome =
> Referenz). Der Befund unten ist der Stand nach dem Regel-7-Fix (Commit
> `7d094fd`) und enthält **Chrome-Messdaten** (Playwright-Probes), die der
> Vorgänger-Zyklus erarbeitet hat – die Hypothese ist NICHT vollständig
> abgeleitet, offene Widersprüche sind markiert (§4, „offen").

## 1. Start: Kontext lesen (Pflicht)

- `w3c-css21-tests/RUNBOOK.md` – Befehle, Artefakte, Interpretation, Gotchas
  (inkl. LayoutTreeDiffer-CLI aus §5)
- `w3c-css21-tests/AGENT-PROMPT-floats-rule7.md` – Vorgänger-Auftrag (Regel-7-Fix,
  Commit `7d094fd`): Vollbreiten-Blockboxen + Float-Listen-Propagation +
  Regel-7-Ausnahme + Paint-Reihenfolge. Dessen Befund-Logik übernehmen.
- `engine-css/CSS2-TODO.md` – Kap. 9: Regel-3- und Regel-7-Eintrag (`[x]`),
  UA-body-Margin-Eintrag (offen).
- Skill `verify` – End-to-End-Bau und Seiten-Laden der Engine.
- **Wichtig – aktueller Stand:** Seit `7d094fd` sind die rule3/rule7-Familien
  auf 1,42–1,59 % Rest-Diff (nur UA-body-Margin-Artefakt). Die
  `floats-wrap-top-below-*`-Tests sind DIFF zwischen 2,88 % und 7,93 %
  (Baseline-Zahlen in §4). Die **`floats-wrap-*`-Familie war im
  Regel-7-Zyklus explizit out of scope („eigene Zyklen")** – das ist jetzt
  dieser Zyklus.

## 2. Setup prüfen

```bash
mvn -q install -DskipTests
```

## 3. Arbeitszyklus (ein Fix = ein Durchlauf)

1. **Baseline sichern** (Stand nach `7d094fd`):
   ```bash
   mvn -Pw3c-css21 -pl w3c-css21-tests -am test -Dbrowicy.tests='floats/.*' \
     -Dmaven.test.failure.ignore=true
   cp w3c-css21-tests/target/w3c-css21/latest.json \
      w3c-css21-tests/target/floats-baseline-post-rule7.json
   ```
2. **Rot bestätigen:** Einzeltests
   `-Dbrowicy.tests='floats/floats-wrap-top-below-inline-002l\.xht'` (Windows:
   Regex in doppelte Anführungszeichen).
3. **Triage:** Zuerst den Layout-Tree-Differ aufrufen (Box-Modell-Vergleich
   direkt im Terminal, headless – siehe §5); ergänzend
   `comparisons/.../{chrome,browicy,diff}.png` und die Aqua-Spans (200×50,
   `rgb(0,255,255)`) per PIL auslesen (G>150, B>150, R<120; bbox + Bänder pro
   y-Zeile). Die Chrome-Soll-Geometrie steht in §4.
4. **Lokalisieren:** Test-HTML lesen (Cache:
   `~/.browicy/w3c-css21-suite/<sha>/css21/floats/floats-wrap-top-below-*-*.xht`).
   Zuständiger Code: `desktop/src/main/java/com/browicy/ui/render/RenderLayoutEngine.java`
   – Inline-Branch in `layoutBlockChildren` (`dropBelowFloatsIfNarrow`,
   `floatArea` pro Zeile, `flushInline`), Block-Branch (`blockArea`-Übergabe,
   `blockMinimum`-Drop-Check) und die Regel-7-Ausnahme aus `7d094fd`.
5. **Fixen:** kleinste Änderung, bestehende Muster übernehmen, deutsche
   Javadoc, keine Symptom-Unterdrückung, keine Sonderfälle für diese Tests.
   **Nicht anfassen:** Regel-7-Ausnahme, `blockMinimum`-Drop-Check,
   `excludeFloats`, Paint-Deferral (`deferredFloats`) – sonst regressieren die
   rule3/rule7-Familien.
6. **Grün + Regression:**
   - Einzeltests der Familie: `-Dbrowicy.tests='floats/floats-wrap-top-below-(inline|bfc)-00[23][lr]\.xht'`
   - Kapitel: `-Dbrowicy.tests='floats/.*'` → Vergleich gegen die Baseline aus
     Schritt 1: **keine** Ratio-Verschlechterung außerhalb der bearbeiteten
     Familie; rule3/rule7-Familien unverändert 1,42–1,59 %.
   - `abspos/.*` → weiterhin 16/16 PASS.
   - Standard-Build: `mvn -q install` (DomViewPanelTest 84/84, Guard
     `textAfterFullWidthFloatDropsBelowTheFloat` grün).
7. **Festhalten:** `engine-css/CSS2-TODO.md`-Eintrag in Kap. 9 (Testnamen +
   Diff-Quote vor → nach, beobachtete Chrome-Regel), ein Fix = ein Commit
   (Conventional Commits), Belege im Commit-Body.

## 4. Bekannter Befund (reproduzierbar, Stand 2026-08-15 nach 7d094fd)

### 4.1 Harness-Metriken (Baseline `floats-baseline-post-rule7.json`)

| Test | diffRatio |
|---|---|
| `floats-wrap-top-below-inline-002{l,r}` | 4,995 % |
| `floats-wrap-top-below-bfc-002{l,r}` | 4,995 % |
| `floats-wrap-top-below-inline-003{l,r}` | 2,88 % / 2,91 % |
| `floats-wrap-top-below-bfc-003{l,r}` | 2,88 % / 2,91 % |

### 4.2 Testaufbau

Alle: `body { width: 400px; border: medium solid; }`, zwei leere Floats
(ohne Hintergrund), dann zwei „Spans". Varianten:

| Variante | Floats | Spans |
|---|---|---|
| `inline-002l/r` | left 150×75 + right 300×75 | `display:inline-block; width:200px; height:50px; background:aqua` |
| `inline-003l/r` | left 250×75 + right 250×75 | `display:inline-block; width:100px; height:50px; background:aqua` |
| `bfc-002l/r` | left 150×75 + right 300×75 | `display:block; overflow:hidden; width:200px; height:50px; background:aqua` |
| `bfc-003l/r` | left 250×75 + right 250×75 | `display:block; overflow:hidden; width:100px; height:50px; background:aqua` |

In allen Fällen: 150+300 > 400 bzw. 250+250 > 400 → der zweite Float passt
nicht neben den ersten und wird in Chrome **unter** den ersten verschoben.

### 4.3 Chrome-Geometrie `inline-002l` (gemessen, browser tool / Playwright)

Body-Content-Koordinaten (Doc = +8/+11 durch body-Margin + border):

- left-Float: (0,0)–(150,75); right-Float: **(100,75)–(400,150)** (unter den
  left-Float verschoben, y=75 = dessen bottom).
- Span 1: **(150,0)–(350,50)** – Zeilenbox 1 liegt **neben** dem left-Float
  auf y=0 (nur der left-Float schmälert sie; der right-Float ist erst ab
  y=75 aktiv).
- Span 2: **(0,150)–(200,200)**; Span 3: (200,150)–(400,200) – Zeilenboxen 2+3
  liegen bei y=150 = **unter dem right-Float** (nicht bei y=50, obwohl dort
  neben dem left-Float 250 px frei wären!).
- `inline-002r` ist das Spiegelbild (rechte Spans bei x=11..210, gleiche
  y-Bänder (11,60) und (161,210)).

### 4.4 Chrome-Probe-Matrix (Playwright, `getBoundingClientRect`, body 400 px)

Vom Vorgänger-Zyklus systematisch vermessen (Block-Content-Koordinaten,
l=left-Float 150 breit, r=right-Float 300 breit, h25/h75 = Höhe, Spans
200×50):

| Fall | Floats | Zeilenboxen | Besonderheit |
|---|---|---|---|
| l75+r75, 2 Spans | l (0..150, 0..75); r (100..400, 75..150) | y=0 (x=150); y=150 (x=150) | Zeile 1 neben l75; Zeile 2 unter r75 **bei x=150** (!), nicht x=0 |
| l75+r75, 1 Span | dito | y=150 (x=150) | – |
| l25+r75, 2 Spans | l (0..150, 0..25); r (100..400, 25..100) | y=100 (x=0); y=100 (x=200) | **keine** Zeile neben l25 |
| l25+r75, 1 Span | l (0..150, 0..25); **r (100..400, 50..125)** | y=125 (x=150) | r bei y=50 statt 25 (!) |
| l25+r50, 2 Spans | l (0..150, 0..25); **r (100..400, 50..100)** | y=100 (x=0); y=100 (x=200) | r bei y=50 statt 25 (!) |
| l75+r50, 2 Spans | l (0..150, 0..75); r (100..400, 75..125) | y=0 (x=150); y=125 (x=150) | – |
| **l25, 2 Spans** | l (0..150, 0..25) | **y=50 (x=0); y=50 (x=200)** | **offen:** keine Zeile neben l25 (250 px frei), Zeilen bei y=50 statt 25 |
| r75+l25, 2 Spans | r (100..400, 0..75); l (0..150, 75..100) | y=75 (x=150); y=125 (x=0) | Zeile 1 neben l25; Zeile 2 darunter |

### 4.5 Abgeleitete Teil-Regel (bestätigt) vs. offene Punkte

**Bestätigt (passt auf alle 8 Fälle):** Eine Zeilenbox weicht allen Floats
aus, deren **vertikale Ausdehnung** die Zeilenbox überlappt (nicht nur dem
Float an der Zeilenbox-Oberkante). Passt der Inhalt nicht in den
verbleibenden Streifen, wird die Zeile **unter den tiefsten überlappenden
Float** geschoben. Das erklärt B1/B5/B6/B8 (Zeilen unter dem verschobenen
r-Float) und die y=0-Zeile neben l75 in B2/B6 (Zeilenbox 0..50 liegt
vollständig im Bereich 0..75 des l75).

**Offen (Widersprüche zur Teil-Regel):**
1. **l25-Allein-Fall (B7):** Zeile bei y=50, nicht y=0 neben dem Float
   (250 px frei, Teil-Regel sagt „passt"), und nicht y=25 (= Float-bottom).
2. **l75+r75, Zeile 2 (B2):** Zeile bei y=150 hat x=150, nicht x=0 – der
   linke Offset stammt offenbar nicht von der finalen y.
3. **l25+r75/l25+r50 mit y=50-Drop:** der zweite Float wird bei y=50
   platziert statt beim bottom des l25 (y=25) – abhängig von Span-Anzahl/
   Float-Höhe (B4/B5 vs. B1). riecht nach **Re-Layout** (siehe 4.6).

### 4.6 Vermuteter Mechanismus in Chrome (Blink NG)

Die Abweichungen deuten auf Blinks Multi-Pass-BFC-Offset-Auflösung hin
(`third_party/blink/renderer/core/layout/block_layout_algorithm.cc`,
`HandleFloat`/`PositionOrClearFloat`): Floats werden optimistisch am
„expected BFC block offset" platziert; wenn der BFC-Offset durch die erste
In-Flow-Zeile aufgelöst wird und abweicht, wird das Layout mit
`abort_when_bfc_block_offset_updated_` **abgebrochen und neu begonnen**.
Zeilenboxen werden gegen `GetExclusionSpace().AllLayoutOpportunities`
platziert (Opportunities = vertikale Streifen zwischen Float-Unterkanten).
Browicy ist Single-Pass ohne BFC-Offset-Re-Layout – die offenen Punkte
können ein Re-Layout (zweiter Durchlauf bei veränderter Float-Position)
oder eine Opportunity-Suche in `layoutBlockChildren` erfordern. Bevor Code
geändert wird: mit Playwright-Probes (siehe §5) die offenen Fälle
nachmessen und die Regel vervollständigen.

### 4.7 bfc-Varianten

`bfc-002/003`: Die Spans sind `display:block; overflow:hidden` =
BFC-Wurzeln (Regel 5: dürfen Floats nicht überlappen). Browicy legt sie im
Block-Branch über `blockArea`/`blockMinimum`-Check; Chrome platziert
BFC-Wurzeln in der ersten passenden Layout-Opportunity. Die bfc-Tests
haben exakt dieselben Floats wie die inline-Tests – die bfc-Geometrie per
Playwright nachmessen (die BFC-Wurzel ist der „Span", x/y direkt messbar).

## 5. Werkzeug: LayoutTreeDiffer-CLI (primär) + Playwright-Probe (bei Bedarf)

**Primär: Der Dual-Engine-Layout-Differ vergleicht die DOM-Box-Geometrie
(Bounding-Boxes + Computed Styles) direkt zwischen headless Chromium und
Browicy in-process – ohne Harness-Lauf, ohne Pixel-Raten:**

```bash
mvn -q install -DskipTests   # einmalig: Modul-Abhängigkeiten installieren
mvn -Pw3c-css21 -pl w3c-css21-tests compile exec:java \
  -Dexec.mainClass=com.browicy.css21.LayoutTreeDiffer \
  -Dexec.skip=false \
  "-Dexec.args=floats/floats-wrap-top-below-inline-002l.xht"
# Varianten: --json (strukturiert), --out <datei> (zusätzlich schreiben)
# Exit-Codes: 0 = Boxen passen, 1 = Layout-Diffs, 2 = Fehler
# Hinweis: exec.skip=false nötig (Parent-POM sperrt exec global); kein -am
# (sonst läuft exec auf jedem Reaktor-Modul); Chromium endet immer headless
# und wird am Laufende geschlossen.
```

Die Tabelle zeigt pro Element Chrome- vs. Browicy-Rect plus
`dx,dy,dWidth,dHeight` und Style-Diffs – damit lässt sich z. B.
`floats-wrap-top-below-inline-002l` sofort auf die Zeilenbox-Positionen
(§4.3) prüfen. Derselbe Vergleich liegt nach jedem Harness-Lauf als
`comparisons/<test>/layout-diff.txt|json` vor.

**Bei Bedarf zusätzlich: Playwright-Probe (Chrome direkt messen).** Die
Harness-`chrome.png` zeigt nur Pixel, keine Box-Rects; für neue/offene
Geometriefragen Chrome direkt fragen (browser tool oder `node -e` mit
Playwright aus `w3c-css21-tests`-Abhängigkeit):

```html
<!DOCTYPE html><html><head><style>
body { width: 400px; }
span { display: inline-block; vertical-align: top; width: 200px; height: 50px; background: aqua; }
.l { float: left; width: 150px; height: 75px; outline: 3px solid red; }
.r { float: right; width: 300px; height: 75px; outline: 3px solid green; }
</style></head><body>
<div class="l"></div><div class="r"></div><span id="s1"></span><span id="s2"></span>
</body></html>
```

`getBoundingClientRect` je Element; Varianten (Höhen 25/50/75, Reihenfolge,
Span-Anzahl) systematisch durchspielen, bis die Regel aus §4.5 alle Fälle
abdeckt. Ergebnis-Matrix in den Commit-Body.

## 6. Scope-Grenzen (wichtig)

- **Nicht anfassen:**
  - rule3/rule7-Familien (fertig; die Regel-7-Ausnahme, der
    `blockMinimum`-Drop-Check, `excludeFloats` und `deferredFloats` aus
    `7d094fd` bleiben unangetastet).
  - `floats-wrap-bfc-00{4,5,6,7}` und `floats-wrap-bfc-outside-001`
    (Tabellen-/Textarea-/Margin-Kollaps-Gemisch, eigene Zyklen; bei
    `bfc-007` ist die Paint-Reihenfolge bereits korrigiert, Rest = Tabellen-
    Breite/Margin-Kollaps).
  - `floats-placement-vertical-*` und `floats-zero-height-wrap-*` (eigene
    Zyklen).
  - UA-body-Margin (8 px) – kein pauschaler body-Margin.
- **Ein Feature pro Zyklus:** Nur die `floats-wrap-top-below-*`-Familie
  (Zeilenbox-/Opportunity-Platzierung unter Floats). Wenn das Re-Layout
  nötig wird: erst den minimalen Teil (Zeilenbox-Vertikalausdehnungs-Regel
  aus §4.5) umsetzen und die offenen Punkte als eigenen Zyklus benennen –
  nicht halbfertig committen.
- **Tests nie verändern.** `skip.txt` nur für interaktive/Nicht-Visual-Tests.
- Befunde belegen statt raten: Report-Metriken, diff.png, Chrome-Probe-
  Messung (getBoundingClientRect), betroffener Code.

## 7. Lieferung pro Fix

- Zusammenfassung: Testpfade, Diff-Quote **vor → nach** (Basis =
  Baseline-Datei aus Schritt 1 bzw. §4.1-Tabelle), geänderte Dateien,
  Spezifikationsreferenz (§9.5.1, Link auf
  https://www.w3.org/TR/CSS2/visuren.html#floats) und die beobachtete
  Chrome-Regel (aus der Probe-Matrix).
- Beleg: Report-Zeile und diff.png-Pfad; Chrome/Browicy-Vergleichswerte
  (bbox) für mindestens einen repräsentativen Test je Variante.
- Am Session-Ende: Fortschrittsübersicht `floats/.*` (PASS/DIFF-Zählung,
  Ratio-Deltas gegen Baseline) und die nächsten drei konkreten Schritte.

## 8. Definition of Done

- **Wrap-Fix umgesetzt:** Die `floats-wrap-top-below-inline-00{2,3}{l,r}`-
  Tests nähern sich der Chrome-Geometrie aus §4.3/§4.4 (Zeilenboxen unter
  dem verschobenen Float, erste Zeile neben dem ersten Float). Idealfall:
  Rest-Diff nur noch das dokumentierte UA-body-Margin-Artefakt (8 px x/y,
  Diff-Bänder wie rule3-Familie) oder eine nachweislich kleinere, begründete
  Diff-Quote. Die bfc-Varianten folgen derselben Opportunity-Logik.
- **Keine neuen DIFFs / keine Verschlechterungen:** `floats/.*` gegen
  Baseline (Schritt 1); insbesondere rule3/rule7-Familien unverändert
  1,42–1,59 %.
- `abspos/.*` weiterhin 16/16 PASS; `mvn -q install` grün
  (DomViewPanelTest 84/84 inkl. `textAfterFullWidthFloatDropsBelowTheFloat`).
- `CSS2-TODO.md` aktualisiert (Kap. 9: Wrap-Eintrag + Chrome-Regel-Notiz),
  Commit mit Belegen (Probe-Matrix!) gesetzt.
