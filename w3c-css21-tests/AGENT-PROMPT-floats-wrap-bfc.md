# Agenten-Auftrag: `floats-wrap-bfc-*` — Presentational Hints `table[width/height]` + TableLayout-Refactoring (Tabellenbreite 300px-Soll)

> **Ziel:** Browicy wendet die HTML-Attribute `width`/`height` auf `table` nicht an
> (Presentational Hints, CSS2.1 §6.4.4 / HTML4-`width`-Attribut): Alle
> `floats-wrap-bfc-00{1,3,4,5,6,7}`-Tests deklarieren die äußere Tabelle mit
> `<table width="300">`; Browicy rendert sie shrink-to-fit (150–250px) statt
> 300px. Nach dem UA-Body-Defaults-Zyklus (Commit `50cda63`, 8px/11px-Floor
> entfernt) sind das die größten verbleibenden floats-Diffs (maxΔPos 50–267px).
> Rot → Triage → Fix in der Kaskaden-/Tabellen-Logik → grün(er) im
> Chrome-vs-Browicy-Harness (Chrome = Referenz). Der Befund unten ist der
> Stand nach Commit `50cda63` und enthält exakte Messdaten aus den
> Harness-layout-diffs.

## 1. Kontext lesen (Pflicht)

- `w3c-css21-tests/RUNBOOK.md` – Befehle, Artefakte, Interpretation, Gotchas
  (LayoutTreeDiffer-CLI, Baseline-Gate-Prozedur).
- `w3c-css21-tests/AGENT-PROMPT-floats-ua-defaults.md` – Vorgänger-Zyklus
  (UA-Body-Margin 8px + border-width-Keywords thin/medium/thick, Commit
  `50cda63`): Gate-Prozedur und Triage-Regeln übernehmen.
- `w3c-css21-tests/AGENT-PROMPT-floats-wrap.md` – Wrap-Fix (Commit `acde4ee`):
  zeilenweise Float-Opportunities (`FloatExclusionSpace.lineSlot`), BFC-Zwei-
  Pass, `blockMinimum`-Drop-Check, Regel-5-BFC-Wurzeln. **Deren BFC-
  Platzierungs-Logik ist die Basis für das Sekundärproblem (§3.2) — nichts
  davon regressieren.**
- `engine-css/CSS2-TODO.md` – Kap. 6: Presentational-Hints-Eintrag (offen,
  P2), Kap. 17: Tabellen-Layout-Algorithmen (offen, P3) — dort eintragen,
  sobald beantwortet.
- Skill `verify` – End-to-End-Bau und Seiten-Laden der Engine.

**Wichtig – aktueller Stand (Commit `50cda63`, Baseline für diesen Zyklus):**

| Test | diffRatio | maxΔPos | Anmerkung |
|---|---|---|---|
| `wrap-bfc-001-left-{overflow,table}` | 0,0521 % | **100** | Tabelle 150 statt 300 |
| `wrap-bfc-001-right-{overflow,table}` | 0,0729 % | **150** | Tabelle 150 statt 300 |
| `wrap-bfc-002-{left,right}-{overflow,table}` | 0,0000 % | 0 | **PASS** (Kontrolle, s. §3.1) |
| `wrap-bfc-003-left-overflow` | 0,0521 % | **100** | Tabelle 150 statt 300 |
| `wrap-bfc-003-left-table` | 0,0156 % | 0 | nur Pixel (Breite 300? Triage!) |
| `wrap-bfc-003-right-overflow` | 0,0625 % | **100** | Tabelle 250 statt 300 |
| `wrap-bfc-003-right-table` | 0,0260 % | **50** | Tabelle 250 statt 300, Float-x falsch |
| `wrap-bfc-004` | 0,1217 % | **200** | Tabelle 150×36 statt 300×20 |
| `wrap-bfc-005` | 0,0869 % | **200** | Tabelle 200 statt 300; innere `width="50%" height="20"` |
| `wrap-bfc-006` | 0,1496 % | **267** | Tabelle 230 statt 300; clear-Floats |
| `wrap-bfc-007` | 0,1225 % | **58** | Margin-Kollaps-Gemisch |
| `wrap-bfc-outside-001` | 0,0305 % | 0 | textarea (replaced), NICHT dieser Zyklus |

Die übrigen floats-Familien sind nach `50cda63` PASS (rule3/rule7) bzw. auf
dokumentierten Rest-Diffs (wrap-top-below ~0,006 %, nur Border-Farbe);
`abspos/.*` 16/16 PASS.

## 2. Setup prüfen

```bash
mvn -q install -DskipTests
```

## 3. Arbeitszyklus

### 3.1 Baseline sichern (Stand `50cda63`)

```bash
mvn -Pw3c-css21 -pl w3c-css21-tests -am test "-Dbrowicy.tests=(floats|abspos)/.*" \
  -Dmaven.test.failure.ignore=true
cp w3c-css21-tests/target/w3c-css21/latest.json /c/temp/bfc-baseline.json
cp -r w3c-css21-tests/target/w3c-css21/comparisons /c/temp/bfc-baseline-comparisons
```

### 3.2 Rot bestätigen + Befund (gemessen in `50cda63`)

Layout-Diff `floats-wrap-bfc-001-left-table` (CLI, siehe §5):

```
DIFF | html > body:nth-of-type(1)        | (8.0, 8.0) 784.0x100.0 | (8.0, 8.0) 784.0x150.0 | +0.0x+50.0
DIFF | ... > table:nth-of-type(1)        | (8.0, 8.0) 300.0x100.0 | (8.0, 8.0) 150.0x150.0 | -150.0x+50.0
     -> Style-Diff [width]: Chrome='300px', Browicy='150px'
DIFF | ... > table:nth-of-type(2)        | (108.0, 8.0) 150.0x50.0 | (8.0, 108.0) 150.0x50.0 | -100.0x+100.0
```

Testaufbau (`floats-wrap-bfc-001-left-table.xht`): `<table width="300">` mit
einer Zelle, darin ein `float:left`-Div (100×100) und eine innere Tabelle mit
einem 150×50-Span. Chrome: äußere Tabelle 300 breit, innere Tabelle **neben**
dem Float (x=108). Browicy: Tabelle 150 (shrink-to-fit), innere Tabelle
**unter** dem Float (y=108).

`floats-wrap-bfc-003-right-table`: Chrome 300×150, Browicy 250×150 (Float bei
x=158 statt 208 — Folge der 250er-Breite). `floats-wrap-bfc-004`: Chrome
300×20, Browicy 150×36; body 160 vs 288 hoch (Umbrechen bei falscher Breite).
`floats-wrap-bfc-005`: Chrome 300×40, Browicy 200×34; innere Tabelle hat
zusätzlich `width="50%" height="20"` (Attribute!). `floats-wrap-bfc-006`:
Chrome 300×76, Browicy 230×60; 11 `float:left; clear:left`-Divs (150…100px)
in der Zelle.

### 3.3 Zwei getrennte Ursachen

1. **Presentational Hints fehlen (primär, alle bfc-Tabellen-Tests):** Das
   `width="300"`-Attribut auf `<table>` wird nirgends ausgewertet — width/height
   kommen in `RenderTreeBuilder.resolveStyle` nur aus CSS-Deklarationen
   (`declarations.get("width")`). Die Tabelle läuft in den `auto`-Zweig von
   `layoutTable` und wird shrink-to-fit (min-content der Zelle). Chrome wendet
   das Attribut als Presentational Hint an (Priorität: UA < Attribut <
   Autor-CSS, CSS2.1 §6.4.4). **Kontroll-Test `wrap-bfc-002` (PASS):** gleiche
   Struktur, aber ZWEI Spans (150+150=300 Inhalt) → Browicys shrink-to-fit
   trifft zufällig 300. Das beweist, dass der Fehler das Attribut-Handling ist,
   nicht die Tabellen-Logik an sich.
2. **BFC-Platzierung der inneren Tabelle (sekundär, nur bfc-001):** Die innere
   BFC-Wurzel (Tabelle; overflow-Varianten analog) wird unter den Float gelegt
   statt in die erste Layout-Opportunity daneben (150+100=250 ≤ 300 passt).
   Das ist der Regel-5-Pfad aus dem Wrap-Zyklus (`blockArea`/Opportunity-
   Suche) — prüfen, warum die Tabelle dort nicht neben den Float rutscht
   (bei bfc-002 ist die BFC-Wurzel 300 breit → passt wirklich nicht daneben →
   dort korrekt unter).

### 3.4 Lokalisieren

- `desktop/src/main/java/com/browicy/ui/render/RenderLayoutEngine.java`:
  `layoutTable` (≈ Z. 1468), `tableRows` (≈ Z. 1577), `fitColumns` (≈ Z. 1640)
  — dort entscheidet `style.width().isAuto()` über `targetWidth` =
  shrink-to-fit vs. spezifizierte Breite.
- `engine-render/.../RenderTreeBuilder.resolveStyle`: width/height-Auflösung
  (≈ Z. 947) — hier fehlt der Preshint-Fallback aus `element.getAttribute(...)`.
- Chrome-Referenz-Verhalten (per Playwright-Probe oder `getComputedStyle` im
  Differ): `table width="300"` → `width: 300px`; `width="50%"` → `width: 50%`;
  `height="20"` → `height: 20px`. Autor-CSS `width` überschreibt das Attribut.
  Auch `td`/`th`-`width`-Attribute und `td[height]` (wirkt wie min-height)
  existieren in der Suite — für diesen Zyklus reicht `table` (inkl. `%`-Wert),
  andere Elemente nur dokumentieren.

### 3.5 Refactoring (empfohlen, explizit erlaubt)

Extrahiere `layoutTable`/`tableRows`/`fitColumns` in eine isolierte,
unit-testbare Komponente `TableLayout` (Muster: `FloatExclusionSpace`/
`InlineLayout` aus `cb397be`), damit die Tabellenbreiten-Logik
(specifiedWidth vs. shrink-to-fit vs. min/max) isoliert testbar wird.

- **Refactoring ohne Diff:** Vorher/Nachher müssen die Nicht-Betroffenen
  Tests byte-identisch sein (Gate §3.6); Unit-Tests für die extrahierte Logik
  (z. B. `TableLayoutTest`: specifiedWidth 300 vs. auto bei 150-Inhalt).
- Wenn die Tabellen-Logik bereits sauber liegt: kein Refactoring nötig —
  kleinste Änderung.

### 3.6 Fixen + Gate

1. Fix anwenden (kleinste Änderung, deutsche Javadoc, keine Symptom-
   Unterdrückung, keine Sonderfälle für die bfc-Tests). **Nicht anfassen:**
   Regel-3/7-Ausnahmen, `blockMinimum`-Drop-Check, `excludeFloats`,
   `deferredFloats` aus dem Wrap-Zyklus — sonst regressieren rule3/rule7/
   wrap-top-below.
2. **Gate 1 – Harness:** Volle Suite (Kommando aus §3.1) neu laufen. Vergleich
   mit `/c/temp/bfc-baseline.json` (jq/python auf
   `path/diffRatio/maxLayoutPositionDelta/status`, sortiert): **KEIN Test darf
   schlechter werden**; die bfc-Tabellen-Tests müssen deutlich besser sein
   (100/150/200/267px → ≤1px erwartet für bfc-001/003/004/005; bfc-006/007
   breiten-abhängig besser). Falls ein Nicht-bfc-Test schlechter wird: Ursache
   finden, nicht zurückrollen.
3. **Gate 2 – App-Tests:** `mvn -q install` — `DomViewPanelTest` (84) und
   `FloatExclusionSpaceTest` (18) müssen grün sein. **Falls die Preshint-
   Änderung App-Snapshots verschiebt** (App-Tests mit `width`-Attributen auf
   Tabellen): prüfen, ob die neuen Werte dem Chrome-Verhalten entsprechen
   (Attribut = Breite); nur dann erwartete Werte rebaselinen (Annahme
   explizit im Test setzen oder Erwartungswert anpassen — Muster aus dem
   UA-Zyklus) und im Commit dokumentieren. Wenn die App die Attribute bewusst
   ignorieren will: dort Gegenregel — nicht die Engine kastrieren.

## 4. Abnahme (muss alles erfüllen)

- `floats-wrap-bfc-001-{left,right}-{overflow,table}` + `floats-wrap-bfc-003-*`
  + `floats-wrap-bfc-004` + `floats-wrap-bfc-005`: maxLayoutPositionDelta
  **≤ 1px** (Tabellenbreite 300px = Chrome; bfc-001 zusätzlich: innere BFC-
  Wurzel neben dem Float).
- `floats-wrap-bfc-006`/`007`: soweit breiten-bedingt besser (dokumentierter
  Rest = clear-Floats bzw. Margin-Kollaps, eigene Zyklen §8).
- `floats-wrap-bfc-002-*`: weiterhin PASS (Kontroll-Test!).
- `floats-wrap-bfc-outside-001`: unverändert oder besser (textarea, NICHT
  Scope — keine Verschlechterung).
- **Kein Test schlechter** als Baseline `50cda63` (Gate §3.6.2, 0
  Regressionen); abspos 16/16 PASS unverändert; rule3/rule7 weiterhin PASS.
- `DomViewPanelTest` 84/84 (oder dokumentierte Rebaseline) +
  `FloatExclusionSpaceTest` 18/18; `mvn -q install` grün.
- `engine-css/CSS2-TODO.md`: Kap. 6-Presentational-Hints-Eintrag auf `[x]`
  (oder mit Begründung anders beantwortet, mindestens `table[width/height]`),
  Kap. 17-Tabellen-Eintrag um die Breiten-Entscheidung ergänzen.

## 5. Werkzeug: LayoutTreeDiffer-CLI (primär) + Harness

```bash
mvn -q install -DskipTests   # einmalig; IMMER nach Engine-Änderung, denn:
# die CLI ohne -am nutzt die INSTALLIERTE desktop-Jar — nach Änderungen
# erst installieren, sonst misst du den alten Stand!
mvn -Pw3c-css21 -pl w3c-css21-tests compile exec:java \
  -Dexec.mainClass=com.browicy.css21.LayoutTreeDiffer \
  -Dexec.skip=false \
  "-Dexec.args=floats/floats-wrap-bfc-001-left-table.xht"
```

- Exit-Codes: 0 = Boxen passen, 1 = Diffs, 2 = Fehler. Optionen `--json`,
  `--out <datei>`.
- `-Dexec.skip=false` ist Pflicht (Parent-POM sperrt exec global), **kein
  `-am`** (exec liefe auf jedem Reaktor-Modul). Alle Maven-Befehle vom
  Repo-Root, nie aus `target/`.
- Die Harness-layout-diffs (`comparisons/<test>/layout-diff.txt|json`) sind
  nach jedem Suite-Lauf aktuell — für die Breiten-Frage reichen
  `floats-wrap-bfc-001-left-table` + `floats-wrap-bfc-005`.

**Bei Bedarf Playwright-Probe (Chrome direkt messen):** für die
Preshint-Semantik `getComputedStyle(document.querySelector('table')).width`
bei `width="300"`/`width="50%"`/`height="20"` + Autor-CSS-Override
(Chrome = Referenz für die Prioritätsregel).

## 6. Triage-Notiz

- Style-Diff `margin-left/right` auf Floats/Tabellen (z. B. Browicy='634px')
  = bekanntes Reporting-Quirk (`RenderLayoutMetrics.horizontalMargin`
  liefert positionsabgeleitete Used Values) — KEIN Layout-Fehler, nicht jagen.
- `html`/`head` und `display:none`-Subbäume werden in der Extraktion
  absichtlich übersprungen (XML-Parsing, getAttribute-Pfad-Parität).
- Pixel-Harness (diffRatio) bleibt das PASS/DIFF-Gate; der Layout-Differ ist
  das Triage-Werkzeug.
- `floats-wrap-bfc-003-left-table` (0,0156 %, maxΔPos 0): nur Pixel-Rest,
  Positionsfehler 0 — nach dem Breiten-Fix nachmessen; wenn die Positionen
  passen, Rest als Paint-Diff (initial `#1c1b1f`?) dokumentieren.

## 7. Lieferung

1. `README.md`/`RUNBOOK.md`: Artefakt-Status aktualisieren (bfc-Tabellen-
   Eintrag auf gelöst/ersetzt durch den tatsächlichen Rest-Diff).
2. Commit (Conventional Commits, z. B.
   `fix(engine-css): Presentational Hints table[width/height] - Tabellenbreite 300px-Soll der bfc-Familie`),
   mit Belegen: Vorher/Nachher-Zahlen (bfc-001/003/004/005 50–267px → ≤1px),
   Gate-Ergebnis (0 Regressionen), DomViewPanelTest-Status (Rebaseline
   dokumentiert falls nötig), CSS2-TODO-Update.

## 8. Ausblick (NICHT dieser Zyklus, nur dokumentieren)

- `floats-wrap-bfc-006`: clear-Floats in Tabellenzellen (Rest nach Breiten-
  Fix; 161 Elemente, clear:left-Stapelung in der Zelle) — eigener Zyklus.
- `floats-wrap-bfc-007`: Margin-Kollaps-Gemisch (6px-Margins zwischen
  Block-Divs; CSS2-TODO Kap. 8: P3 Margin-Kollaps offen) — eigener Zyklus.
- `floats-wrap-bfc-outside-001`: `textarea` als replaced element (Browicy
  0×21 statt 486×139) — eigener Zyklus (replaced-Inline/Block-Sizing, §10.3).
- `floats-placement-vertical-*`: p-UA-Defaults (top-margin 0 statt 1em) +
  Margin-Kollaps + Float-in-p (0×21-Boxen) — eigener Zyklus (362–370px).
- Initiale Textfarbe `#1c1b1f` statt Schwarz (Border-/Text-Farbe, Rest-Diff
  der wrap-top-below-Familie ~0,006 %) — eigener Zyklus.
