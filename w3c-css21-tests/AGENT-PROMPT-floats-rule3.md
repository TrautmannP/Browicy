# Agenten-Auftrag: Float-Regel 3 (CSS2.1 §9.5.1) testgetrieben umsetzen

> **Ziel:** Die Browicy-CSS-Engine gegen den W3C-CSS2.1-Test
> `floats/floats-rule3-outside-left-001.xht` abgleichen: Die Platzierung
> eines zweiten Floats im selben BFC folgt der Float-Regel 3 (Kanten-
> Bedingung, nicht Überlappungs-Vermeidung). Rot → Triage → Fix in
> `engine-render` → grün im Chrome-vs-Browicy-Harness (Chrome = Referenz).

## 1. Start: Kontext lesen (Pflicht)

- `w3c-css21-tests/RUNBOOK.md` – Befehle, Artefakte, Interpretation, Gotchas
- `engine-css/CSS2-TODO.md` – Kap. 9 (Visuelles Formatierungsmodell) und der
  Stand der bereits abgehakten Einträge (§10.1-ICB-Fix, Commit `43e8fc3`)
- `w3c-css21-tests/UPSTREAM.md` – Suite-Herkunft und Pin
- Skill `verify` – End-to-End-Bau und Seiten-Laden der Engine
- **Wichtig – aktueller Stand:** Seit dem ICB-Fix (Commit `43e8fc3`) ist das
  Wurzelelement (html) Teil des Render-Trees, der ICB = Viewport am
  Canvas-Ursprung, `window.scrollTo` funktioniert, und das Kapitel
  `abspos/.*` ist 16/16 PASS. Der floats-Test ist NICHT mehr „~300 px zu
  tief“ im Groben – der verbleibende Diff ist klein (≈1,8 %), siehe §4.

## 2. Setup prüfen

```bash
mvn -q install -DskipTests
```

## 3. Arbeitszyklus (ein Fix = ein Durchlauf)

1. **Rot bestätigen** (Referenz existiert bereits):

   ```bash
   mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
     "-Dbrowicy.tests=floats/floats-rule3-outside-left-001\.xht"
   ```

   Windows-Konsole: Regex in doppelte Anführungszeichen setzen.
2. **Triage:** `w3c-css21-tests/target/w3c-css21/latest.html` öffnen,
   `comparisons/floats_floats-rule3-outside-left-001.xht/{chrome,browicy,diff}.png`
   ansehen; `latest.json` für Metriken. Die blauen Pixel exakt auslesen
   (siehe §4 – die erwarteten Soll-Koordinaten sind bekannt).
3. **Lokalisieren:** Test-HTML lesen (Cache:
   `~/.browicy/w3c-css21-suite/<sha>/css21/floats/floats-rule3-outside-left-001.xht`
   – die Selbstbeschreibung zitiert §9.5.1-Regel 3). Zuständiger Code:
   `desktop/src/main/java/com/browicy/ui/render/RenderLayoutEngine.java`
   (Float-Branch in `layoutBlockChildren`, `FloatRegion`, `floatArea`,
   `clearedY`, `dropBelowFloatsIfNarrow`).
4. **Fixen:** kleinste Änderung, bestehende Muster übernehmen, deutsche
   Javadoc, keine Symptom-Unterdrückung (kein `passRatio`-Grünfärben, keine
   Sonderfälle für diesen Test).
5. **Grün + Regression:**
   - Einzeltest: `-Dbrowicy.tests='floats/floats-rule3-outside-left-001\.xht'`
   - Kapitel: `-Dbrowicy.tests='floats/.*'` → keine neuen DIFFs
   - Standard-Build: `mvn -q install` (inkl. bestehender Tests)
6. **Festhalten:** `engine-css/CSS2-TODO.md`-Eintrag in Kap. 9
   abhaken/annotieren (Testname + Diff-Quote), ein Fix = ein Commit
   (Conventional Commits, z. B. `fix(engine-render): apply float rule 3
   outer-edge constraint in BFC`), Belege im Commit-Body.

## 4. Bekannter Befund (reproduzierbar, Stand 2026-08-15 nach ICB-Fix)

| Test | Beobachtung (Chrome vs. Browicy) | Verdacht |
|---|---|---|
| `floats/floats-rule3-outside-left-001.xht` | blaue 425×10-Box bei **Chrome (8, 8)–(432, 17)**, bei **Browicy (0, 300)–(424, 309)** → 300 px zu tief | Float-Regel 3: Browicy weicht der rechten Float-Box vertikal aus statt nur die äußere Kante zu prüfen |

**Testaufbau:** äußerer `float:left; width:500; height:500` (BFC); darin
`float:right; width:50; height:300` (rechte Kante), dann ein Block mit
`margin-right:100` (Inhaltsbreite 400) mit einem `float:left; width:425;
height:10; background:blue` darin.

**Pass-Kriterium (Referenz `floats-rule3-outside-left-001-ref.xht`):** Die
blaue Box liegt oben im BFC (y ≈ 8), NICHT unterhalb der rechten Float-Box.
Regel 3 (§9.5.1): Die rechte Außenkante eines left-Floats darf nicht rechts
der linken Außenkante eines right-Floats liegen, das rechts davon steht.
Hier: blau 0–425 im BFC, rechtes Float ab 450 → 425 < 450 → erlaubt, Box
bleibt oben. Browicy interpretiert die verfügbare Breite des Enthalten-
den-Blocks (400 < 425) als Platzierungs-Hindernis und schiebt die Box auf
y = 300 (unter die rechte Float-Box) – das ist die falsche Regel.

**Diff-Metriken aktuell:** `diffRatio ≈ 1,77 %` (mean 4,5, max 255), zwei
Diff-Bänder: Zeilen **8–17** (Chrome blau / Browicy weiß, x 8–432) und
**300–309** (Browicy blau / Chrome weiß, x 0–424), je 4250 Pixel.

**Code-Stellen (Hypothese):** Im Float-Branch von `layoutBlockChildren`
wird `floatArea`/`dropBelowFloatsIfNarrow` mit der Breite des
Containing-Blocks gerechnet; die Kanten-Bedingung der Regel 3 (Bezug auf
die äußeren Kanten der übrigen Floats im selben BFC, nicht auf die
verfügbare Breite des eigenen Containing-Blocks) fehlt.

## 5. Scope-Grenzen (wichtig)

- **Nicht anfassen:** Der horizontale 8-px-Versatz (Chrome x=8 vs. Browicy
  x=0) ist der Chrome-UA-body-Margin (8 px), den Browicy nicht modelliert
  (`defaultMargin("body") = 0`). Das ist ein UA-Default-Unterschied, KEIN
  Regel-3-Fehler. Wenn nach dem Regel-3-Fix nur noch dieser Versatz übrig
  ist (erwartete Rest-Diff ≈ 0,9 % = 4250 Pixel einer 425×10-Box):
  - KEIN `browicy.refreshReferences=true` setzen (nur für bewusste
    Baseline-Änderungen, RUNBOOK),
  - KEINEN pauschalen body-Margin im Engine-Code ergänzen – das würde alle
    aktuell grünen Tests (z. B. `abspos/.*`, 16/16 PASS) um 8 px verschieben
    und als neue DIFFs brechen,
  - stattdessen als eigenes Thema („UA-Defaults / Baseline-Entscheidung“)
    im CSS2-TODO.md Kap. 9 vermerken und im Session-Bericht benennen.
- **Ein Feature pro Zyklus:** Nur Regel 3. Nicht gleichzeitig
  `block-in-inline-margins-001a` (Border-Ring leerer Boxen, §8) oder
  Parser-Lücken anfassen.
- **Tests nie verändern.** `skip.txt` nur für interaktive/Nicht-Visual-Tests.
- Befunde belegen statt raten: Report-Metriken, diff.png, Testinhalt,
  betroffener Code.

## 6. Lieferung pro Fix

- Zusammenfassung: Testpfad, Diff-Quote **vor → nach**, geänderte Dateien,
  Spezifikationsreferenz (§9.5.1, Link auf
  https://www.w3.org/TR/CSS2/visuren.html#floats).
- Beleg: Report-Zeile (PASS oder Rest-Diff mit Begründung) und
  diff.png-Pfad; bei Layout-Fixes optional Chrome/Browicy-Vergleichsbild.
- Am Session-Ende: Fortschrittsübersicht `floats/.*` (PASS/DIFF-Zählung)
  und die nächsten drei konkreten Schritte.

## 7. Definition of Done

- **Regel-3-Fix umgesetzt:** blaue Box liegt vertikal an der Position der
  Referenz (y ≈ 8), nicht mehr bei y = 300; die beiden Diff-Bänder
  verschwinden; Rest-Diff ist höchstens das dokumentierte UA-body-Margin-
  Artifakt (x-Versatz 8 px, ≈ 0,9 %).
- **Keine neuen DIFFs:** `floats/.*`-Kapitel-Regression zeigt keine neuen
  DIFFs gegenüber dem Stand vor dem Fix.
- `mvn -q install` grün (Standard-Tests unverändert grün, insbesondere
  `abspos/.*`-Harness-Lauf 16/16 PASS).
- `CSS2-TODO.md` aktualisiert (Kap. 9: Regel-3-Eintrag + Rest-Artifakt-
  Notiz), Commit mit Belegen gesetzt.
