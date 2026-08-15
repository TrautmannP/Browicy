# Agenten-Auftrag: W3C CSS2.1-Suite testgetrieben abarbeiten

> **Ziel:** Die Browser-CSS-Engine (Browicy) Stück für Stück gegen die W3C
> CSS2.1-Testsuite abgleichen. Jede offene Implementierung aus
> `engine-css/CSS2-TODO.md` wird mit Suite-Tests belegt (rot), in `engine-*`
> implementiert und im Chrome-vs-Browicy-Harness grün gemacht
> (Test-Driven Development, Chrome = Referenz).

## 1. Start: Kontext lesen (Pflicht)

- `w3c-css21-tests/RUNBOOK.md` – Befehle, Artefakte, Interpretation, Gotchas
- `engine-css/CSS2-TODO.md` – Lückenanalyse der CSS2.1-Kapitel (P1/P2 = Parser/Kaskade, P3 = Layout/Render)
- `w3c-css21-tests/UPSTREAM.md` – Suite-Herkunft und Pin
- Skill `verify` – End-to-End-Bau und Seiten-Laden der Engine

## 2. Setup prüfen

```bash
mvn -q install -DskipTests                      # baut alle Module
```

- Chromium muss installiert sein. Die Testsuite lädt der Harness beim ersten
  Lauf (gepinnte Revision, Cache `~/.browicy/w3c-css21-suite/<sha>/`).
- Die Ahem-Schrift ist für Text-Tests relevant; ohne sie sind
  Schrift-Glyphen-Diffs kein Engine-Fehler (siehe Triage).

## 3. Arbeitszyklus (ein Fix = ein Durchlauf)

1. **Testmenge wählen** – Einstieg: die bekannten Funde aus Abschnitt 6 oder
   ein TODO-P1-Kapitel (z. B. `borders/.*`). Immer so eng wie möglich.
2. **Rot machen** (Referenzen entstehen automatisch):

   ```bash
   mvn -Pw3c-css21 -pl w3c-css21-tests -am test -Dbrowicy.tests='<regex>'
   ```

   Windows-Konsole: Regex in doppelte Anführungszeichen setzen
   (`"abspos/abspos-containing-block-initial-001\.xht"`).
3. **Triage:** `w3c-css21-tests/target/w3c-css21/latest.html` öffnen,
   `diff.png` ansehen. Unterscheiden:
   - **Font-Rauschen** (kleine `diffRatio` 1–3 %, nur Textzeilen bzw.
     1-px-Kanten magenta, `maxAbsDiff` klein) → kein Engine-Fix; notieren,
     weiter.
   - **Struktur-Gap** (Box fehlt/verschoben/gefüllt, große Flächen magenta)
     → Engine-Fix, weiter mit 4.
4. **Lokalisieren:** Test-HTML lesen (Cache:
   `~/.browicy/w3c-css21-suite/<sha>/css21/<pfad>.xht` – die
   Selbstbeschreibung im Test nennt das Pass-Kriterium). Betroffenes
   Verhalten und Spezifikationskapitel (aus `CSS2-TODO.md`) benennen, dann
   die zuständige Stelle in `engine-*` suchen (Parser → `engine-css`,
   Layout/Paint → `engine-render`, Selektoren → `engine-selectors`).
5. **Fixen:** kleinste Änderung, bestehende Muster übernehmen, deutsche
   Javadoc, keine Symptom-Unterdrückung (kein `passRatio`-Grünfärben, keine
   Sonderfälle).
6. **Grün + Regression:**
   - Einzeltest: `-Dbrowicy.tests='<genauer test>\.xht'` → PASS
   - Kapitel: `-Dbrowicy.tests='<kapitel>/.*'` → keine neuen DIFFs
   - Standard-Build: `mvn -q install` (inkl. bestehender Tests)
7. **Festhalten:** `engine-css/CSS2-TODO.md`-Eintrag abhaken/annotieren
   (Testname + Diff-Quote), ein Fix = ein Commit (Conventional Commits,
   z. B. `feat(engine-render): support initial containing block for abs positioned boxes`),
   Belege im Commit-Body (Harness-Report-Zeile, Bildpfad).

## 4. Regeln

- **Tests nie verändern.** `skip.txt` nur für interaktive/Nicht-Visual-Tests
  (`:hover`/`:active`/cursor/print), nicht um DIFFs zu verstecken.
- `browicy.refreshReferences=true` nur für bewusste Baseline-Änderungen
  (z. B. neue Chrome-Version), nie zum Grünfärben.
- **Ein Feature pro Zyklus.** Nicht mehrere Lücken gleichzeitig anfassen;
  Reihenfolge entlang der TODO-Prioritäten.
- Befunde belegen statt raten: Report-Metriken, diff.png, Testinhalt,
  betroffener Code. Bei Unklarheit gezielt nachsehen, nicht spekulieren.
- Umfang respektieren: kleine gefilterte Läufe iterieren (`-q`), volle
  Suite (~9 800 Tests, 1–3 h) nur als Abschluss-Check eines Meilensteins.

## 5. Lieferung pro Fix

- Zusammenfassung: Testpfad, Diff-Quote **vor → nach**, geänderte Dateien,
  Spezifikationsreferenz (Kapitel + Link).
- Beleg: Report-Zeile (PASS) und diff.png-Pfad; bei Layout-Fixes optional
  Chrome/Browicy-Vergleichsbild.
- Am Session-Ende: Fortschrittsübersicht des bearbeiteten Kapitels
  (PASS/DIFF-Zählung) und die nächsten drei konkreten Schritte.

## 6. Bekannte Funde zum Einstieg (reproduzierbar, 2026-08-15)

| Test | Beobachtung (Chrome vs. Browicy) | Verdacht | TODO |
|---|---|---|---|
| `abspos/abspos-containing-block-initial-001.xht` | gelbe/braune Eckboxen fehlen nach `window.scrollTo` | Initial containing block / Viewport-Positionierung | Kap. 10 (§10.1) |
| `floats/floats-rule3-outside-left-001.xht` | blauer Float ~300 px zu tief bei zweitem Float im selben BFC | Float-Regel 3 / constraint box | Kap. 9 (§9.5.1) |
| `normal-flow/block-in-inline-margins-001a.xht` | Border leerer Block-in-Inline-Box gefüllt statt Rahmenring | Border-Painting leerer Boxen | Kap. 8 |

**Arbeitsbeispiel für den ersten Fund:**

```bash
# rot
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  "-Dbrowicy.tests=abspos/abspos-containing-block-initial-001\.xht"
# → DIFF, diffRatio ≈ 8,6 %

# Triage: test-HTML lesen → 4 absolute divs an Viewport-Ecken, body 10000px,
# scrollTo(0,50). Chrome: Boxen bleiben an den Ecken (ICB = Viewport).
# Browicy: gelbe/braune Boxen fehlen → Layout löst ICB falsch auf.
# Lokalisieren: RenderTreeBuilder/LayoutEngine, containing block für
# position:absolute ohne positionierten Vorfahren (§10.1), fixen, neu bauen.
mvn -q install -DskipTests
# grün
mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
  "-Dbrowicy.tests=abspos/abspos-containing-block-initial-001\.xht"
# → PASS; danach Kapitel-Regression abspos/.*, CSS2-TODO.md abhaken, commiten
```

## 7. Definition of Done

- Zieltest: **PASS** im Harness (pixelidentisch, `passRatio` unverändert).
- Kapitel-Regression: keine **neuen** DIFFs gegenüber dem Stand vor dem Fix.
- `mvn -q install` grün (Standard-Tests unverändert grün).
- `CSS2-TODO.md` aktualisiert, Commit mit Belegen gesetzt.
