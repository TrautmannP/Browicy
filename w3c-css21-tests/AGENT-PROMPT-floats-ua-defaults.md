# Agenten-Auftrag: UA-Body-Defaults — der 8px-/11px-Floor (CSS2.1-Anhang A / Chrome-Referenz)

> **Ziel:** Die Browicy-CSS-Engine wendet die Chrome-Referenz-UA-Defaults für
> `body` nicht an: **`body { margin: 8px }` fehlt** (alle floats-Familien sind
> dadurch bei 1,42–2,8 % Diff gedeckelt, maxΔPos 8px) und **Autor-Borders auf
> `body` werden nicht in die Box übernommen** (`body { border: medium solid }`
> → Browicy border-width 0px, Box 400 statt 406, bei den wrap-top-below-Tests
> zusätzliche 3px). Beides zusammen = der **11px-Floor** (8px Margin + 3px
> Border). Rot → Triage → Fix in der UA-Stylesheet-Anwendung → grün(er) im
> Chrome-vs-Browicy-Harness (Chrome = Referenz). Der Befund unten ist der
> Stand nach Commit `eb226f8` (Wrap-Fix) und enthält exakte Messdaten aus den
> Harness-layout-diffs.

## 1. Kontext lesen (Pflicht)

- `w3c-css21-tests/RUNBOOK.md` – Befehle, Artefakte, Interpretation, Gotchas
  (LayoutTreeDiffer-CLI, Baseline-Gate-Prozedur)
- `w3c-css21-tests/AGENT-PROMPT-floats-wrap.md` – Vorgänger-Auftrag
  (Wrap-Fix, Commit `eb226f8`): zeilenweise Float-Opportunities
  (`FloatExclusionSpace.lineSlot`), BFC-Zwei-Pass, Floats schieben den Flow
  nicht mehr. Dessen Befund-Logik und Gate-Prozedur übernehmen.
- `engine-css/CSS2-TODO.md` – Kap. 9: UA-body-Margin-Eintrag (offen), dort
  eintragen, sobald beantwortet.
- Skill `verify` – End-to-End-Bau und Seiten-Laden der Engine.

**Wichtig – aktueller Stand (Commit `eb226f8`, Baseline für diesen Zyklus):**
- rule3/rule7-Familien: 1,42–1,59 % Diff, maxΔPos **8px** (reines
  UA-body-Margin-Artefakt, seit drei Zyklen dokumentiert).
- wrap-Familie: 12/26 Tests auf **11px** (8px Margin + 3px Body-Border),
  Rest (wrap-bfc-001..006) = separates Tabellen-Sizing-Problem (siehe §8,
  NICHT dieser Zyklus).
- abspos/.* 16/16 PASS; alle Nicht-Wrap-Familien byte-identisch zu
  `cb397be` (Baseline-Gate grün).
- **Dieser Zyklus ist der erste, der bewusst Engine-Verhalten ändert, das
  alle Familien betrifft** — die Gate-Prozedur (§3.3) ist deshalb Pflicht.

## 2. Setup prüfen

```bash
mvn -q install -DskipTests
```

## 3. Arbeitszyklus

### 3.1 Baseline sichern (Stand `eb226f8`)

```bash
mvn -Pw3c-css21 -pl w3c-css21-tests -am test "-Dbrowicy.tests=(floats|abspos)/.*" \
  -Dmaven.test.failure.ignore=true
cp w3c-css21-tests/target/w3c-css21/latest.json /c/temp/ua-baseline.json
cp -r w3c-css21-tests/target/w3c-css21/comparisons /c/temp/ua-baseline-comparisons
```

### 3.2 Rot bestätigen (Befund, gemessen in `eb226f8`)

Layout-Diff `floats-rule7-outside-left-001` (CLI, siehe §5):

```
DIFF | html > body:nth-of-type(1) | Chrome (8.0, 8.0) 784.0x0.0 | Browicy (0.0, 0.0) 800.0x0.0
     -> Style-Diff [margin-top]: Chrome='8px', Browicy='0px'   (alle vier Seiten)
     -> Style-Diff [width]: Chrome='784px', Browicy='800px'
```

Layout-Diff `floats-wrap-top-below-inline-001l` (Body mit `border: medium solid`):

```
DIFF | body | Chrome (8.0, 8.0) 406.0x106.0 | Browicy (0.0, 0.0) 400.0x100.0
     -> Style-Diff [border-*-width]: Chrome='3px', Browicy='0px'
```

Zwei getrennte Probleme, beide im UA-/Box-Handling:

1. **UA-body-Margin fehlt:** Browicy liefert für `body` margin 0px, Chrome 8px
   (CSS2.1-Anhang A / HTML-Referenz-UA-Sheet). Folge: jede Box in den
   floats-Tests sitzt 8px links/oben versetzt — der seit drei Zyklen bekannte
   Floor.
2. **Autor-Border auf `body` fehlt:** `border: medium solid` (Autor-Regel)
   ergibt Browicy border-width 0px und eine Content-Box ohne Dekoration
   (400 statt 406). Das ist KEIN UA-Artefakt, sondern ein echter Bug —
   vermutlich Root-/Body-Sonderbehandlung in der Style-Anwendung oder in der
   Box-Berechnung. Zuerst prüfen, ob `border` auf einem beliebigen
   `display:block` funktioniert (Probe), dann eingrenzen.

### 3.3 Lokalisieren

- Wo werden UA-Defaults definiert? Vermutlich `engine-css`
  (`StyleApplicator` oder ein Default-Stylesheet) bzw. das html-Modul —
  lokalisieren (grep nach `margin`-Defaults, `body`-Regeln). **Bevor du
  änderst: klären, warum heute margin 0 ist** (explizite `body { margin: 0 }`
  Regel? Fehlende Defaults? Absicht der App?).
- Wo wird die Body-Border verschluckt? `RenderLayoutEngine.layoutBlock`
  (Box-Berechnung `borderBoxWidth`/`borderBoxHeight`),
  `RenderStyle.borderWidth()`, Root-Sonderfälle (`html`/`body`-Skipping in
  der Style-Anwendung?). Eingrenzen mit einer Playwright-freien Probe:
  kleines HTML mit `body { border: medium solid }` + einem Div mit Border
  — funktioniert der Div-Border? (Ja → Body-Spezialfall. Nein → genereller
  Border-Bug.)

### 3.4 Refactoring (empfohlen, explizit erlaubt)

Falls die UA-Defaults **verstreut oder hartkodiert** sind (String-Regeln in
Code, magische Werte an mehreren Stellen): extrahiere sie in eine isolierte,
unit-testbare Komponente (z. B. `engine-css/src/main/java/.../UaDefaults.java`
oder eine geprüfte Ressource), die die Chrome-Referenz-Defaults
**zentralisiert** (body margin 8px, html-Margins, h1–h6/p/ul-Defaults als
Doku-Set für spätere Familien). Regeln dafür:

- **Refactoring ohne Diff:** Vorher/Nachher müssen die Nicht-Betroffenen
  Tests byte-identisch sein (Gate §3.5). Unit-Tests für die extrahierte
  Logik.
- KEIN vollständiger UA-Audit in diesem Zyklus (Scope: body margin + body
  border; andere Default-Differenzen nur dokumentieren, nicht fixen).
- Wenn die Defaults bereits zentral an einer sauberen Stelle liegen: kein
  Refactoring nötig — kleinste Änderung.

### 3.5 Fixen + Gate

1. Fix anwenden (kleinste Änderung, deutsche Javadoc, keine
   Symptom-Unterdrückung, keine Sonderfälle für die floats-Tests).
2. **Gate 1 – Harness:** Volle Suite (Kommando aus §3.1) neu laufen lassen.
   Vergleichen mit `/c/temp/ua-baseline.json` (jq auf
   `path/diffRatio/maxLayoutPositionDelta/status`, sortiert, wie im
   Wrap-Zyklus): **KEIN Test darf schlechter werden**; rule3/rule7/wrap
   müssen deutlich besser sein (8px/11px → 0–1px erwartet). Falls ein
   Nicht-Body-Test schlechter wird: Ursache finden, nicht zurückrollen.
3. **Gate 2 – App-Tests:** `mvn -q install` — `DomViewPanelTest` (84) und
   `FloatExclusionSpaceTest` (18) müssen grün sein. **Falls die
   UA-Änderung App-Snapshots verschiebt** (Body-Margin betrifft jede
   gerenderte Seite): prüfen, ob die neuen Snapshots dem Chrome-Verhalten
   entsprechen (Margin oben/links); nur dann erwartete Bilder
   regenerieren (DomViewPanelTest-Mechanik im Test lesen) und die
   Rebaseline im Commit dokumentieren. Wenn die App die Margin bewusst
   vermeiden will (z. B. eigenes App-CSS), dort die Gegenregel setzen —
   nicht die Engine kastrieren.
4. Falls der Body-Border-Bug tiefer sitzt (genereller Border-Bug): Fixen,
   aber Gate 1 erneut (Border ändert alle Boxen mit Border — die
   floats-Tests haben keine weiteren Borders, aber die App-Tests).

## 4. Abnahme (muss alles erfüllen)

- `floats-rule7-outside-left/right-001`: maxLayoutPositionDelta **8px → ≤1px**
  (Chrome (8,8) vs Browicy (8,8)); Style-Diff margin 8px/0px verschwindet.
- wrap-Familie: die 12 Tests auf **11px → ≤1px** (body 406×106 = Browicy
  406×106 bei Border-Tests).
- **Kein Test schlechter** als Baseline `eb226f8` (Gate §3.5.2, 0
  Regressionen); abspos 16/16 PASS unverändert.
- `DomViewPanelTest` 84/84 (oder dokumentierte Rebaseline) +
  `FloatExclusionSpaceTest` 18/18; `mvn -q install` grün.
- `engine-css/CSS2-TODO.md`: UA-body-Margin-Eintrag auf `[x]` (oder mit
  Begründung anders beantwortet).

## 5. Werkzeug: LayoutTreeDiffer-CLI (primär) + Harness

```bash
mvn -q install -DskipTests   # einmalig; IMMER nach Engine-Änderung, denn:
# die CLI ohne -am nutzt die INSTALLIERTE desktop-Jar — nach Änderungen
# erst installieren, sonst misst du den alten Stand!
mvn -Pw3c-css21 -pl w3c-css21-tests compile exec:java \
  -Dexec.mainClass=com.browicy.css21.LayoutTreeDiffer \
  -Dexec.skip=false \
  "-Dexec.args=floats/floats-rule7-outside-left-001.xht"
```

- Exit-Codes: 0 = Boxen passen, 1 = Diffs, 2 = Fehler. Optionen `--json`,
  `--out <datei>`.
- `-Dexec.skip=false` ist Pflicht (Parent-POM sperrt exec global), **kein
  `-am`** (exec liefe auf jedem Reaktor-Modul). Alle Maven-Befehle vom
  Repo-Root, nie aus `target/`.
- Die Harness-layout-diffs (`comparisons/<test>/layout-diff.txt|json`) sind
  nach jedem Suite-Lauf aktuell — für die Body-Probleme reicht
  `floats-rule7-...` + `floats-wrap-top-below-inline-001l`.

## 6. Triage-Notiz

- Style-Diff `margin-right` auf Floats (z. B. `Browicy='300px'`) = bekanntes
  Reporting-Quirk (`RenderLayoutMetrics.horizontalMargin` liefert
  positionsabgeleitete Used-Values) — KEIN Layout-Fehler, nicht jagen.
- `html`/`head` und `display:none`-Subbäume werden in der Extraktion
  absichtlich übersprungen (XML-Parsing, getAttribute-Pfad-Parität).
- Pixel-Harness (diffRatio) bleibt das PASS/DIFF-Gate; der Layout-Differ ist
  das Triage-Werkzeug.

## 7. Lieferung

1. `README.md`/`RUNBOOK.md`: Artefakt-Status aktualisieren (8px-Floor-Eintrag
   auf gelöst/ersetzt durch den tatsächlichen Rest-Diff; Body-Border-Hinweis).
2. Commit (Conventional Commits, z. B.
   `fix(engine-css): UA-body-margin und Body-Border - 8px/11px-Floor der floats-Familien entfernt`),
   mit Belegen: Vorher/Nachher-Zahlen (rule7 8→≤1px, wrap 11→≤1px), Gate-Ergebnis
   (0 Regressionen), DomViewPanelTest-Status (Rebaseline dokumentiert falls
   nötig).

## 8. Ausblick (NICHT dieser Zyklus)

- `floats-wrap-bfc-001..006` (108–267px): **Tabellen-Sizing mit Floats**
  (Tabelle 300px-Soll wird 150px geschrumpft, shrink-to-fit zählt Floats
  fälschlich als Inhalt). Eigener Zyklus; Refactoring-Vorschlag dort:
  `layoutTable`/`tableRows`/`fitColumns` in eine `TableLayout`-Komponente
  extrahieren (Muster: `FloatExclusionSpace`/`InlineLayout` aus `cb397be`),
  damit die Tabellenbreiten-Logik isoliert unit-testbar wird.
- Restdifferenzen nach diesem Zyklus (falls >0): mit dem Layout-Differ
  einzeln nachmessen und als neue Familien-Prompts ausformulieren.
