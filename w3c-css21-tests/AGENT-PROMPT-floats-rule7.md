# Agenten-Auftrag: Float-Regel 7 (CSS2.1 §9.5.1) testgetrieben umsetzen

> **Ziel:** Die Browicy-CSS-Engine gegen die W3C-CSS2.1-Tests
> `floats/floats-rule7-outside-left-001.xht` und
> `floats/floats-rule7-outside-right-001.xht` abgleichen: Normale
> In-Flow-Blockboxen fließen vertikal und horizontal „als gäbe es den Float
> nicht" (§9.5.1) – der verbleibende Diff kommt aus dem Float-geschmälerten
> Layout normaler Blöcke. Rot → Triage → Fix in `engine-render` → grün im
> Chrome-vs-Browicy-Harness (Chrome = Referenz).

## 1. Start: Kontext lesen (Pflicht)

- `w3c-css21-tests/RUNBOOK.md` – Befehle, Artefakte, Interpretation, Gotchas
  (Lücken-Liste ist auf dem Stand nach dem Regel-3-Fix)
- `engine-css/CSS2-TODO.md` – Kap. 9: neuer Regel-3-Eintrag (`[x]`, Commit
  `ecdf061`) + Notiz „UA-Defaults / Baseline-Entscheidung (body-Margin)"
- `w3c-css21-tests/AGENT-PROMPT-floats-rule3.md` – Vorgänger-Auftrag; dessen
  Befund-Logik (Band-Analyse, Baseline-Vergleich via `git stash`) übernehmen
- Skill `verify` – End-to-End-Bau und Seiten-Laden der Engine
- **Wichtig – aktueller Stand:** Seit dem Regel-3-Fix (Commit `ecdf061`)
  ist die rule3-Familie (`floats-rule3-outside-{left,right}-00{1,2}`) auf
  1,42–1,59 % Rest-Diff = ausschließlich UA-body-Margin-Artefakt (8 px x/y,
  diff.png: nur die versetzten Blau-Bänder). Die rule7-Tests sind durch
  denselben Fix von 1,77/1,68 % auf 1,45/1,29 % gefallen, haben aber noch
  einen **echten** Rest (siehe §4).

## 2. Setup prüfen

```bash
mvn -q install -DskipTests
```

## 3. Arbeitszyklus (ein Fix = ein Durchlauf)

1. **Baseline sichern** (zwingend, um Regressionen zu messen – Stand nach
   `ecdf061`):
   ```bash
   mvn -Pw3c-css21 -pl w3c-css21-tests -am test -Dbrowicy.tests='floats/.*' \
     -Dmaven.test.failure.ignore=true
   cp w3c-css21-tests/target/w3c-css21/latest.json \
      w3c-css21-tests/target/floats-baseline-post-rule3.json
   ```
2. **Rot bestätigen** (Referenzen existieren bereits):
   ```bash
   mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
     "-Dbrowicy.tests=floats/floats-rule7-outside-(left|right)-001\.xht"
   ```
   Windows-Konsole: Regex in doppelte Anführungszeichen.
3. **Triage:** `w3c-css21-tests/target/w3c-css21/latest.html` öffnen,
   `comparisons/floats_floats-rule7-outside-{left,right}-001.xht/{chrome,browicy,diff}.png`
   ansehen; blaue Box exakt auslesen (PIL: B>150, R<120, G<120; bbox + Anzahl;
   Diff-Bänder pro y-Zeile). Die Soll-Koordinaten stehen in §4.
4. **Lokalisieren:** Test-HTML lesen (Cache:
   `~/.browicy/w3c-css21-suite/<sha>/css21/floats/floats-rule7-outside-*-001.xht`).
   Zuständiger Code: `desktop/src/main/java/com/browicy/ui/render/RenderLayoutEngine.java`
   – `layoutBlockChildren` (Übergabe von `blockArea` an `layoutBlock` für
   normale Blöcke), `FloatRegion`/`floatArea`/`clearedY`/
   `dropBelowFloatsIfNarrow`, Intrinsic-Breiten mit `excludeFloats` (aus
   `ecdf061`).
5. **Fixen:** kleinste Änderung, bestehende Muster übernehmen, deutsche
   Javadoc, keine Symptom-Unterdrückung, keine Sonderfälle für diese Tests.
   Erwartete Stoßrichtung (Hypothese, §4): normale In-Flow-Blöcke werden mit
   `(blockArea.x(), blockArea.width())` gelegt – stattdessen `(contentX,
   contentWidth)`, und die **Zeilenboxen** (nicht die Blockbox) weichen den
   Floats des BFC aus. Dazu muss die aktive Float-Liste des BFC in
   Nicht-BFC-Kinder propagiert werden (aktuell ist sie pro
   `layoutBlockChildren` lokal). **Risiko:** Text in verschachtelten Blöcken
   würde ohne Propagation unter den Float laufen – Guard:
   `DomViewPanelTest.textAfterFullWidthFloatDropsBelowTheFloat` (p nach
   100-%-Float: Zeilenbox unter dem Float, genau 1 Zeilenbox). Erst
   minimaler Eingriff; wenn die Propagierung zu groß wird, im Report als
   eigenen Zyklus benennen und die Zwischenlösung (nur volle Breite, ohne
   Zeilenbox-Ausweichen) verwerfen statt sie halbfertig zu committen.
6. **Grün + Regression:**
   - Einzeltests: `-Dbrowicy.tests='floats/floats-rule7-outside-(left|right)-001\.xht'`
   - Kapitel: `-Dbrowicy.tests='floats/.*'` → Vergleich gegen die in Schritt 1
     gesicherte Baseline: keine neuen DIFFs, keine Ratio-Verschlechterung;
     rule3-Familie unverändert 1,42–1,59 %
   - `abspos/.*` → weiterhin 16/16 PASS (body-Margin darf nicht angefasst
     werden)
   - Standard-Build: `mvn -q install`
7. **Festhalten:** `engine-css/CSS2-TODO.md`-Eintrag in Kap. 9
   (Testnamen + Diff-Quote vor → nach), ein Fix = ein Commit (Conventional
   Commits, z. B. `fix(engine-render): lay out normal blocks at full
   containing width independent of floats`), Belege im Commit-Body.

## 4. Bekannter Befund (reproduzierbar, Stand 2026-08-15 nach ecdf061)

| Test | Chrome (gemessen, browser tool) | Browicy (diff.png) | Verdacht |
|---|---|---|---|
| `floats-rule7-outside-left-001` | blau **(108,8)–(532,17)** (Box 425×10; Middle-Div bei x=108, volle Breite 400) | blau **(150,0)–(574,9)** → 50 px zu weit rechts | Normaler Block (Middle-Div) wird bei `blockArea.x()`=50 statt 0 gelegt → Margin 100 → blau bei 150 statt 100 |
| `floats-rule7-outside-right-001` | blau Box **(-17,8)–(408,17)**, sichtbar (0,8)–(407,17) (408 px) | blau Box **(-75,0)–(349,9)**, sichtbar (0,0)–(349,9) (350 px) | Middle-Div bekommt Float-geschmälerte Breite 350 (450−100) statt 400 (500−100) → blaue rechte Kante bei 350 statt 408 |

**Testaufbau (left-001):** äußerer `float:left; width:500; height:500` (BFC);
darin `float:left; width:50; height:300` (linke Kante), dann ein Block mit
`margin-left:100` (Inhaltsbreite 400) mit einem `float:left; width:425;
height:10; background:blue` darin. **right-001** ist das Spiegelbild
(`float:right`, `margin-right:100`, `float:right`-Blaue).

**Regel 7 (Test-Selbstbeschreibung):** „A left-floating box that has another
left-floating box to its left may not have its right outer edge to the right
of its containing block's right edge. (Loosely: a left float may not stick
out at the right edge, **unless it is already as far to the left as
possible**.)" Chrome wendet die Ausnahme an: Die blaue Box liegt an der
äußersten linken Kante ihres Containing-Blocks (x=108 = Div-Kante) und darf
deshalb rechts rausragen (Kante 533 > 508). Browicy ragt ebenfalls raus, aber
an der falschen x-Position.

**Wichtig – Ziel ist Chrome, NICHT die W3C-Referenzdatei:** Die
`-ref.xht` erwartet die blaue Box bei y=300 (`margin-top:300px`); Chrome
rendert den Test bei y=8. Dieser W3C-Test ist in Chrome test-vs-ref ein FAIL
(Chrome implementiert die „as far left as possible"-Ausnahme, die Ref eine
strikte Regel-7-Lesart). Der Harness vergleicht Chrome(test) gegen
Browicy(test) – Ziel ist also `comparisons/.../chrome.png` (blau oben, y≈8),
**nicht** die Ref-Datei. Das nicht „reparieren", die Diskrepanz gehört in den
Report.

**Code-Hypothese:** In `layoutBlockChildren` wird `blockArea`
(`floatArea(floats, contentX, contentWidth, y)`) als `x`/`availableWidth` an
`layoutBlock` für **alle** Block-Kinder übergeben. §9.5.1 verlangt für
normale (Nicht-BFC-)Blöcke: „non-positioned block boxes created before and
after the float box flow vertically as if the float did not exist" – die Box
erhält volle Containing-Block-Breite, nur ihre Zeilenboxen werden um Floats
verkürzt. Die rule3-Korrektur (`ecdf061`) hat den vertikalen Drop-Check
entkoppelt (Float-Nachfahren zählen nicht zur Mindestbreite), die
Breiten-/x-Schmälerung normaler Blöcke bleibt aber bestehen.

## 5. Scope-Grenzen (wichtig)

- **Nicht anfassen:**
  - UA-body-Margin (8 px x/y) – bewusstes Thema „UA-Defaults /
    Baseline-Entscheidung" in CSS2-TODO.md Kap. 9; KEIN pauschaler
    body-Margin, KEIN `refreshReferences` ohne Entscheidung.
  - rule3-Familie (fertig, nur noch Artefakt) und `floats-wrap-*` /
    `floats-placement-vertical-*` / `floats-zero-height-wrap-*` (eigene
    Zyklen).
  - Der `blockMinimum`-Drop-Check und `excludeFloats` aus `ecdf061` bleiben
    unangetastet.
- **Ein Feature pro Zyklus:** Nur Regel 7 (normale Blöcke bei voller Breite).
- **Tests nie verändern.** `skip.txt` nur für interaktive/Nicht-Visual-Tests.
- Befunde belegen statt raten: Report-Metriken, diff.png, Testinhalt,
  Chrome-Messung (browser tool: `getBoundingClientRect`), betroffener Code.
- Guard gegen Regression: `DomViewPanelTest.textAfterFullWidthFloatDropsBelowTheFloat`
  + `mvn -q install` grün.

## 6. Lieferung pro Fix

- Zusammenfassung: Testpfade, Diff-Quote **vor → nach** (Basis =
  Baseline-Datei aus Schritt 1 bzw. §4-Tabelle), geänderte Dateien,
  Spezifikationsreferenz (§9.5.1, Link auf
  https://www.w3.org/TR/CSS2/visuren.html#floats), Hinweis auf die
  Chrome-vs-W3C-Ref-Diskrepanz.
- Beleg: Report-Zeile (PASS oder Rest-Diff mit Begründung) und
  diff.png-Pfad; Chrome/Browicy-Vergleichswerte (bbox).
- Am Session-Ende: Fortschrittsübersicht `floats/.*` (PASS/DIFF-Zählung,
  Ratio-Deltas gegen Baseline) und die nächsten drei konkreten Schritte.

## 7. Definition of Done

- **Regel-7-Fix umgesetzt:** `floats-rule7-outside-left-001` blaue Box bei
  Browicy **(100,0)–(524,9)** (statt (150,0)–(574,9); Chrome (108,8)–(532,17));
  `floats-rule7-outside-right-001` blaue Box sichtbar **(0,0)–(399,9)**, Box
  (-25,0)–(400,9) (statt 350 breit; Chrome sichtbar (0,8)–(407,17)). Rest-Diff
  ist höchstens das dokumentierte UA-body-Margin-Artefakt (8 px x/y) – die
  Diff-Bänder entsprechen denen der rule3-Familie (nur versetzte
  Blau-Bänder).
- **Keine neuen DIFFs / keine Verschlechterungen:** `floats/.*`-Kapitel
  gegen Baseline (Schritt 1); insbesondere rule3-Familie unverändert
  1,42–1,59 % und die Text-Wrap-Fälle (z. B. `DomViewPanelTest`) grün.
- `abspos/.*`-Harness-Lauf weiterhin 16/16 PASS; `mvn -q install` grün.
- `CSS2-TODO.md` aktualisiert (Kap. 9: Regel-7-Eintrag + Diskrepanz-Notiz),
  Commit mit Belegen gesetzt.
