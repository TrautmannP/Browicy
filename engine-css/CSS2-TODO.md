# CSS2.1-Implementierungsstatus der Browicy-Engine

Stand: 2026-08-15 · Modul: `engine-css` (Parser, Kaskade, Selector-Matching) inkl. Konsumenten in `engine-render`

Abgeglichen gegen die [CSS 2.1 Spezifikation (W3C Recommendation, 07.06.2011)](https://www.w3.org/TR/CSS2/).

## Legende

- `- [ ]` offene Implementierung · `- [x]` erledigt
- Schicht: **P**arser · **K**askade · **R**enderer (Layout/Painting in `engine-render`)
- Priorität: `P1` = klein, schneller Gewinn · `P2` = mittel · `P3` = größere Layout-/Renderarbeit

## Überblick

| Kapitel | Status |
|---|---|
| [4 Syntax & Datentypen](https://www.w3.org/TR/CSS2/syndata.html#q4.0) | weitgehend, Lücken bei Einheiten, Zählern, At-Rules |
| [5 Selektoren](https://www.w3.org/TR/CSS2/selector.html#q5.0) | fast vollständig; `\|=`, `:lang()` fehlen |
| [6 Kaskade & Vererbung](https://www.w3.org/TR/CSS2/cascade.html#q6.0) | `@import`, Origins offen; Preshints `table[width/height]` umgesetzt |
| [7 Medientypen](https://www.w3.org/TR/CSS2/media.html#q7.0) | nur `screen`/`all` + min/max-width/height |
| [8 Boxmodell](https://www.w3.org/TR/CSS2/box.html#box-model) | Rahmenwerte unvollständig, kein Margin-Kollaps |
| [9 Visuelles Formatierungsmodell](https://www.w3.org/TR/CSS2/visuren.html#q9.0) | `run-in`, 9.7-Regeln, `unicode-bidi` offen |
| [10 Formatierungsdetails](https://www.w3.org/TR/CSS2/visudet.html#q10.0) | Breiten-/Höhenalgorithmen, `vertical-align` unvollständig |
| [11 Visuelle Effekte](https://www.w3.org/TR/CSS2/visufx.html#q11.0) | `clip` wird nicht gerendert |
| [12 Generierter Inhalt & Listen](https://www.w3.org/TR/CSS2/generate.html#generated-text) | Zähler, Quotes, Listen-Eigenschaften offen |
| [13 Seitenmedien](https://www.w3.org/TR/CSS2/page.html#the-page) | komplett offen |
| [14 Farben & Hintergründe](https://www.w3.org/TR/CSS2/colors.html#q14.0) | weitgehend; `background-attachment: fixed` nicht gerendert |
| [15 Schriften](https://www.w3.org/TR/CSS2/fonts.html#q15.0) | font-size-Keywords, System-Fonts, small-caps offen |
| [16 Text](https://www.w3.org/TR/CSS2/text.html#q16.0) | `word-spacing`, `text-indent`-Rendering, `blink` offen |
| [17 Tabellen](https://www.w3.org/TR/CSS2/tables.html#q17.0) | `caption-side`, `border-spacing`, `empty-cells` offen |
| [18 Benutzeroberfläche](https://www.w3.org/TR/CSS2/ui.html#q18.0) | System-Farben/-Schriften, `cursor: url()`, outline-Werte offen |
| Appendix A Aural | bewusst nicht geplant (visueller Browser) |

---

## 4 Syntax und grundlegende Datentypen

Stand: Deklarations-Parser mit Shorthand-Expansion (`margin`, `border`, `background`, `font`, `flex`, `animation`, `transition`, `grid`), `!important`-Erkennung, Kommentar-Stripping, `var()`-Auflösung, CSS-Farben (`#rgb`/`#rrggbb`/`rgb()`/16 CSS2-Namen).

- [ ] **P1 · `ex`-Einheit fehlt** — `ex` ist in CSS2 eine definierte relative Länge, wird aber von keiner Längen-RegEx akzeptiert ([4.3.2 Lengths](https://www.w3.org/TR/CSS2/syndata.html#length-units)).
- [ ] **P1 · Absolute Längeneinheiten fehlen** — `in`, `cm`, `mm`, `pt`, `pc` werden nirgends geparst ([4.3.2 Lengths](https://www.w3.org/TR/CSS2/syndata.html#length-units)).
- [ ] **P2 · Zähler-Werte `counter()`/`counters()` fehlen** — als `content`-Werte und Datentyp ([4.3.5 Counters](https://www.w3.org/TR/CSS2/syndata.html#counter), Details in Kap. 12).
- [ ] **P2 · Kein Tokenizer, Fehlerbehandlung nach §4.2 unvollständig** — der Parser arbeitet regex-basiert; Fehlerfälle (z. B. At-Rules vor der ersten Regel) führen teils zum Verwerfen ganzer Regeln statt nur der betroffenen Deklaration ([4.2 Rules for handling parsing errors](https://www.w3.org/TR/CSS2/syndata.html#parsing-errors)).
- [ ] **P2 · `@charset` nicht implementiert** — wird stillschweigend übersprungen ([4.4 CSS style sheet representation](https://www.w3.org/TR/CSS2/syndata.html#charset)).

## 5 Selektoren

Stand: Typ-, Universal-, Nachfahren-, Kind-, Geschwister-Selektoren, Attribut-Selektoren (`[att]`, `[att=val]`, `[att~=val]`, `[att^=val]`, `[att$=val]`, `[att*=val]`), Klasse, ID, Pseudoklassen `:first-child`, `:link`, `:visited`, `:hover`, `:active`, `:focus`, Pseudo-Elemente `:first-line`, `:first-letter`, `:before`, `:after`. Spezifität nach §6.4.3 korrekt (IDs > Klassen/Attribute/Pseudoklassen > Typen/Pseudo-Elemente). `@namespace` wird übersprungen, aber nicht aufgelöst.

- [ ] **P1 · Attribut-Selektor `[att|=val]` fehlt** — der einzig verbliebene CSS2-Operator; `|=` (Hyphen-separiert, z. B. `[lang|=en]`) wirft einen Parse-Fehler ([5.8.1 Matching attributes and attribute values](https://www.w3.org/TR/CSS2/selector.html#matching-attrs)).
- [ ] **P1 · `:lang()` matcht nie** — wird geparst, aber `DomSelectorAdapter` kennt keinen `lang`-Fall → immer `false` ([5.11.4 The language pseudo-class](https://www.w3.org/TR/CSS2/selector.html#lang)).
- [ ] **P2 · `:visited` immer `false`** — kein Verlauf/Besuchsstatus im DOM-Modell ([5.11.2 Link pseudo-classes](https://www.w3.org/TR/CSS2/selector.html#link-pseudo-classes)).
- [ ] **P3 · `:first-line`/`:first-letter`-Layout fehlt** — Selektoren und Pseudo-Styles werden verarbeitet, aber die spezielle Formatierung (eingeschränkte Property-Menge, erste Zeile, Initialbuchstabe) ist im Renderer nicht umgesetzt ([5.12.1](https://www.w3.org/TR/CSS2/selector.html#first-line-pseudo), [5.12.2](https://www.w3.org/TR/CSS2/selector.html#first-letter)).

## 6 Kaskade und Vererbung

Stand: Autoren-Stylesheets mit Reihenfolge, Spezifität, `!important`, Inline-`style` (höchste Priorität), `var()`-Kaskade, Index-beschleunigtes Matching ab 64 Regeln.

- [ ] **P1 · `@import` fehlt und verschluckt die erste Regel** — `@import` vor einer Regel wird in die Prelude der Folge-Regel gezogen, die damit unparsebar wird und verworfen wird ([6.3 The @import rule](https://www.w3.org/TR/CSS2/cascade.html#at-import)).
- [ ] **P2 · Kaskaden-Origins fehlen** — es gibt nur Autoren-Stylesheets; keine UA-/User-Origin-Sortierung, kein separater `!important`-Durchlauf pro Origin ([6.4.1 Cascading order](https://www.w3.org/TR/CSS2/cascade.html#cascading-order)).
- [x] **P2 · Presentational Hints: `table[width/height]` umgesetzt** — die HTML-Attribute `width`/`height` auf `table` fließen als CSS-Dimensionen in `RenderTreeBuilder.resolveStyle` ein (Priorität: UA-Defaults &lt; Attribut &lt; Autor-CSS, §6.4.4). Werte sind HTML4-`%Length` (Integer → px, `%` bleibt Prozent); Autor-CSS überschreibt das Attribut. Belegt: `floats-wrap-bfc-00{1,3,4,5}`-Familie — äußere Tabelle rendert 300px statt shrink-to-fit 150–250px (LayoutTreeDiffer maxLayoutPositionDelta 50–200px → 0px), inkl. innerer Tabellen mit `width="50%" height="20"`. Andere Elemente (`img`, `td`/`th`, `bgcolor`/`align`) weiterhin offen ([6.4.4 Precedence of non-CSS presentational hints](https://www.w3.org/TR/CSS2/cascade.html#preshint)).
- [ ] **P2 · Vererbung nicht in der Kaskade modelliert** — vererbende Properties werden im Renderer über Parent-Chaining weitergereicht, nicht als Vererbungsregel der Kaskade; `inherit` wird nur für eine Teilmenge der Properties unterstützt ([6.2 Inheritance](https://www.w3.org/TR/CSS2/cascade.html#inheritance)).

## 7 Medientypen

Stand: `@media`-Blöcke werden verschachtelt geparst; ausgewertet wird nur `(min|max)-(width|height)` in `px` plus `screen`/`all`.

- [ ] **P1 · `not`/`only` falsch ausgewertet** — `not print` ist aktuell immer `false` (müsste am Bildschirm `true` sein), `not` wird generell verworfen ([7.2.1 The @media rule](https://www.w3.org/TR/CSS2/media.html#at-media-rule)).
- [ ] **P2 · Weitere Medientypen fehlen** — `print`, `speech`, `projection`, `handheld`, `tty`, `tv`, `braille`, `embossed`, `aural` werden nie gematcht ([7.3 Recognized media types](https://www.w3.org/TR/CSS2/media.html#media-types)).

## 8 Boxmodell

Stand: `margin`/`padding`/`border` (Shorthands, Seiten-Longhands, logische Eigenschaften), `border-radius`, `box-sizing`, `overflow`; Renderer konsumiert Ränder/Padding/Rahmen.

- [x] **P1 · `border-width`: Keywords `thin|medium|thick` umgesetzt** — der Parser akzeptiert die Keywords in `border`/`border-{side}`/`border-width` und normalisiert sie sofort auf die Chrome-Referenz-Pixel 1px/3px/5px (CSS2.1 §8.5.1), damit der Renderer sie wie Längen auflöst. Belegt: `floats/floats-wrap-top-below-inline-001l` (body `border: medium solid`) meldete border-width 0px und 400×100 statt Chrome 406×106; nach dem Fix 406×106, alle `border-*-width`-Style-Diffs weg. Davor wurde die gesamte Deklaration verworfen (`expandBorder` `return` bei unbekanntem Token), unabhängig vom Element — kein Body-Spezialfall ([8.5.1 Border width](https://www.w3.org/TR/CSS2/box.html#border-width-properties)).
- [ ] **P1 · `border-style`: `hidden|groove|ridge|inset|outset` fehlen** — akzeptiert werden nur `none|solid|dotted|dashed|double` ([8.5.3 Border style](https://www.w3.org/TR/CSS2/box.html#border-style-properties)).
- [ ] **P3 · Margin-Kollaps nicht implementiert** — vertikale Ränder zwischen Geschwistern/Eltern werden additiv behandelt statt kollabiert ([8.3.1 Collapsing margins](https://www.w3.org/TR/CSS2/box.html#collapsing-margins)).

## 9 Visuelles Formatierungsmodell

Stand: `position` (static/relative/absolute/fixed/sticky), Offsets, `float`/`clear`, `z-index`, `display` (alle CSS2-Werte außer `run-in`), `direction`, `visibility`; Renderer konsumiert alle genannten.

- [x] **P3 · Float-Regel 3 (§9.5.1) umgesetzt** — Eine normale In-Flow-Blockbox wird nicht mehr wegen zu geringer Restbreite unter andere Floats geschoben: Der Drop-Check in `layoutBlockChildren` (Float-Branch, `RenderLayoutEngine`) nutzt nur noch die Mindestbreite des In-Flow-Inhalts (Float-Nachfahren per `excludeFloats` in der Intrinsic-Breitenberechnung ausgeschlossen – Floats positionieren gegen die Kanten des Containing-Blocks bzw. anderer Floats, nie gegen die verfügbare Breite des eigenen Containing-Blocks). Floats selbst bleiben über denselben Check mit ihrer eigenen Außenbreite an die Kanten-Bedingung der Regel 3 gebunden (rechte Außenkante eines left-Floats nicht rechts der linken Außenkante eines rechts stehenden right-Floats, [§9.5.1](https://www.w3.org/TR/CSS2/visuren.html#floats)). Belegt: `floats/floats-rule3-outside-{left,right}-00{1,2}.xht` und `floats/floats-rule7-outside-*.xht` verbessern sich je 1,68–1,98 % → 1,29–1,59 % (nur noch UA-Margin-Rest), `floats/.*`-Kapitel (45 Tests) ohne neue DIFFs.
- [x] **P3 · Float-Regel 7 (§9.5.1) umgesetzt** — Normale In-Flow-Blockboxen werden mit voller Containing-Block-Breite gelegt (`contentX`/`contentWidth` statt der Float-geschmälerten `blockArea`); nur ihre Zeilenboxen weichen den Floats des BFC aus. Dafür wird die aktive Float-Liste des BFC durch Nicht-BFC-Kinder propagiert (gemeinsame, mutierbare Liste in `layoutBlockChildren`), BFC-Wurzeln (overflow ≠ visible, table, inline-block, Flex/Grid) bekommen weiterhin `blockArea` + eigene Liste (Regel 5). Float-Regel-7-Ausnahme: Ein Float, der an der Kante seines Containing Blocks anliegt ("so weit links bzw. rechts wie möglich"), darf über die gegenüberliegende Kante hinausragen – nur wenn kein Float der Gegenseite im Containing Block steht (Regel 4). Float-Fragmente werden nach den In-Flow-Blockhintergründen gemalt (Anhang E), nicht in Quellreihenfolge. Belegt: `floats-rule7-outside-left-001` blaue Box bei Browicy (100,0)–(524,9) statt (150,0)–(574,9), Rest-Diff 1,45 % → 1,42 %; `floats-rule7-outside-right-001` Box (-25,0)–(400,9) statt 350 px schmal, Rest-Diff 1,29 % → 1,35 %; Rest-Diff jeweils ausschließlich UA-body-Margin-Artefakt (8 px x/y). Hinweis: Chrome wendet die "as far left/right as possible"-Ausnahme an und fällt damit im W3C-Test-vs-Ref-Vergleich durch; Ziel des Harness ist Chrome(test) vs. Browicy(test), nicht die `-ref.xht`.
- [x] **P2 · UA-Defaults / Baseline-Entscheidung (body-Margin) umgesetzt** — `RenderTreeBuilder.defaultMargin("body")` liefert jetzt 8px auf allen vier Seiten (CSS2.1-Anhang A / Chrome-Referenz-UA-Sheet). Damit verschwand der 8px-Floor: rule3/rule7 je 4/2 Tests DIFF→PASS (maxΔPos 8→0), wrap-top-below 12× maxΔPos 11→0 (8px Margin + 3px Body-Border), wrap-bfc-002 4× PASS, zero-height-wrap 2× PASS. Gate 1 (61 Tests, Baseline vor dem Zyklus): **0 Regressionen** — abspos 16/16 PASS unverändert (alle abspos-Tests setzen `margin: 0` explizit oder positionieren gegen den ICB). Der frühere Einwand („würde abspos um 8px verschieben") war damit widerlegt: Chrome hat die Margin in diesen Tests ebenfalls, Browicy fehlte sie nur. Begleitend meldet `RenderLayoutMetrics.verticalMargin` ohne Kaskaden-Wert jetzt den UA-Default aus dem RenderStyle (analog zu padding/border), damit der Layout-Differ nicht mehr `0px` statt `8px` anzeigt. Rest-Diff der wrap-Familie (~0,006 %) = initiale Textfarbe `#1c1b1f` statt Schwarz als Border-Farbe (currentColor) — separater Paint-Default, dort dokumentiert.
- [ ] **P1 · `display: run-in` fehlt** ([9.2.3 Run-in boxes](https://www.w3.org/TR/CSS2/visuren.html#run-in)).
- [ ] **P2 · §9.7-Wechselbeziehungen nicht umgesetzt** — berechnete Wert-Transformationen (`float` → `none` bei absoluter Positionierung, Blockifizierung von `display`) fehlen ([9.7 Relationships between 'display', 'position', and 'float'](https://www.w3.org/TR/CSS2/visuren.html#dis-pos-flo)).
- [ ] **P2 · `unicode-bidi` fehlt komplett** — Property wird nicht geparst, Bidi-Einbettung/Override nicht umgesetzt; `direction` wird geparst, aber Bidi-Layout gibt es nicht ([9.10 Text direction](https://www.w3.org/TR/CSS2/visuren.html#direction)).

## 10 Details des visuellen Formatierungsmodells

Stand: `width`/`height`/`min-width`/`max-width`/`min-height`/`max-height` (Längen, `%`, `auto`, `calc()`/`min()`/`max()`/`clamp()`), `line-height`, horizontale Auto-Margen, **Initial Containing Block (§10.1)**.

- [x] **P3 · Initial Containing Block (§10.1) umgesetzt** — ICB = Viewport-Maße, am Canvas-Ursprung verankert (scrollt nicht mit). Das Wurzelelement (html) nimmt mit Margin/Border/Padding am Layout teil; positionierte Boxen ohne positionierten Vorfahren sowie das selbst positionierte Wurzelelement positionieren gegen den ICB, Prozentwerte des Wurzelelements lösen gegen die ICB-Dimensionen auf. `window.scrollTo`/`scroll`/`scrollBy` verschieben den Canvas beim Malen. Belegt: `abspos/abspos-containing-block-initial-*` (16/16 PASS im Harness, pixelidentisch; vorher 16/16 DIFF).

- [ ] **P2 · `vertical-align`: `sub`, `super`, Längen- und Prozentwerte fehlen** — Parser akzeptiert sie nur teils, der Renderer bildet alles Unbekannte auf `baseline` ab ([10.8 Line height calculations](https://www.w3.org/TR/CSS2/visudet.html#line-height)).
- [ ] **P3 · Breitenalgorithmen §10.3 unvollständig** — replaced Inline-Elemente, absolut positionierte Elemente, Inline-Block-Konstellationen ([10.3 Calculating widths and margins](https://www.w3.org/TR/CSS2/visudet.html#Computing_widths_and_margins)).
- [ ] **P3 · Höhenalgorithmen §10.6 unvollständig** — v. a. absolut positionierte Elemente und `auto`-Höhen für BFC-Wurzeln ([10.6 Calculating heights and margins](https://www.w3.org/TR/CSS2/visudet.html#Computing_heights_and_margins)).
- [ ] **P3 · min/max-Klemmungsreihenfolge fehlt** — §10.4/§10.7 schreiben vor: erst `min-width` gegen `max-width` prüfen, dann auf `used width` anwenden ([10.4](https://www.w3.org/TR/CSS2/visudet.html#min-max-widths), [10.7](https://www.w3.org/TR/CSS2/visudet.html#min-max-heights)).

## 11 Visuelle Effekte

Stand: `overflow` (visible/hidden/scroll/auto/clip, geparst + gerendert), `visibility` (visible/hidden/collapse, geparst + gerendert).

- [ ] **P2 · `clip: rect(...)` wird geparst, aber nicht gerendert** — kein Clipping im Renderer ([11.1.2 Clipping](https://www.w3.org/TR/CSS2/visufx.html#clipping)).

## 12 Generierter Inhalt und Listen

Stand: `content` für `:before`/`:after` mit `normal`/`none`/String/`attr()`; Renderer erzeugt Pseudo-Inhalt; `list-style-type` teilweise; Marker-Rendering (disc/circle/square/none).

- [ ] **P2 · `content`-Werte `open-quote|close-quote|no-open-quote|no-close-quote`, `<uri>`, `counter()`/`counters()` fehlen** ([12.2 The 'content' property](https://www.w3.org/TR/CSS2/generate.html#content)).
- [ ] **P2 · `quotes`-Property fehlt komplett** ([12.3 Quotation marks](https://www.w3.org/TR/CSS2/generate.html#quotes)).
- [ ] **P2 · Zähler fehlen komplett** — `counter-increment`, `counter-reset`, Verschachtelungs-Scope, Zählerstile ([12.4 Automatic counters and numbering](https://www.w3.org/TR/CSS2/generate.html#counters)).
- [ ] **P1 · `list-style-type`: `lower-greek|lower-latin|upper-latin|armenian|georgian` fehlen** ([12.5.1 List properties](https://www.w3.org/TR/CSS2/generate.html#list-style)).
- [ ] **P2 · `list-style-position` und `list-style-image` fehlen** — das `list-style`-Shorthand extrahiert nur den Typ ([12.5.1 List properties](https://www.w3.org/TR/CSS2/generate.html#list-style)).

## 13 Seitenmedien

Stand: nichts implementiert (visueller Bildschirm-Renderer).

- [ ] **P2 · `@page`-Regel fehlt** — wird als unbekannte At-Rule verworfen; Seitenränder, `:left`/`:right`/`:first`-Selektoren fehlen ([13.2 Page boxes](https://www.w3.org/TR/CSS2/page.html#page-box), [13.2.2 Page selectors](https://www.w3.org/TR/CSS2/page.html#page-selectors)).
- [ ] **P2 · `page-break-before|after|inside`, `orphans`, `widows` fehlen** ([13.3.1 Page break properties](https://www.w3.org/TR/CSS2/page.html#page-break-props), [13.3.2 Breaks inside elements](https://www.w3.org/TR/CSS2/page.html#break-inside)).
- [ ] **P2 · Medientyp `print` nicht unterstützt** (siehe Kap. 7) — ohne ihn ist alles oben nur toter Code ([7.3 Media types](https://www.w3.org/TR/CSS2/media.html#media-types)).

## 14 Farben und Hintergründe

Stand: `color`, `background-color`, `background-image`, `background-repeat`, `background-position` (Keywords + Offsets + `%`), `background-size`, `background`-Shorthand, Farben (`#rgb`/`#rrggbb`/`rgb()`/`hsl()` + CSS-Color-3-Keywords inkl. `brown` u. a.), **Canvas-Hintergrund-Propagation (§14.2.1)**.

- [x] **P2 · Canvas-Hintergrund (§14.2.1) umgesetzt** — Hintergrundfarbe des Wurzelelements (bzw. des `body`, wenn `html` transparent ist) wird auf den gesamten Canvas inkl. Scrollbereich propagiert. Belegt: `abspos/abspos-containing-block-initial-004e/f.xht` (PASS).
- [ ] **P2 · `background-attachment: fixed|local` wird geparst, aber nicht gerendert** ([14.2.1 Background properties](https://www.w3.org/TR/CSS2/colors.html#background-properties)).

## 15 Schriften

Stand: `font-family`, `font-style`, `font-weight` (100–900, normal/bold/bolder/lighter), `font-size` (Längen/%), `line-height`, `font`-Shorthand (Stil/Gewicht/Größe/Zeilenhöhe/Familie), `@font-face`.

- [ ] **P1 · `font-family` wird kleingeschrieben** — der Parser lowercased alle Werte; der Computed Value weicht damit von „as specified" ab und Namen mit Großbuchstaben (`"Times New Roman"`) sind nicht mehr zuordenbar ([15.3 Font family](https://www.w3.org/TR/CSS2/fonts.html#font-family-prop)).
- [ ] **P1 · `font-size`-Keywords fehlen** — `xx-small|...|xx-large`, `larger`, `smaller` werden nicht akzeptiert, nur Längen/`%` ([15.7 Font size](https://www.w3.org/TR/CSS2/fonts.html#font-size-props)).
- [ ] **P2 · `font-variant: small-caps` nicht umgesetzt** — Longhand akzeptiert beliebige Werte (keine Validierung), wird aber nie gerendert; im `font`-Shorthand führt `small-caps` zum Verwerfen der ganzen Deklaration ([15.5 Small-caps](https://www.w3.org/TR/CSS2/fonts.html#small-caps)).
- [ ] **P2 · System-Schrift-Schlüsselwörter fehlen** — `font: caption|icon|menu|message-box|small-caption|status-bar` wird verworfen ([15.8 Shorthand font property](https://www.w3.org/TR/CSS2/fonts.html#font-shorthand)).
- [ ] **P3 · Font-Matching-Algorithmus fehlt** — Fallback über die Familienliste und generische Familien (`serif`, `sans-serif`, `cursive`, `fantasy`, `monospace`) sind im Renderer nicht nach §15.2 umgesetzt ([15.2 Font matching algorithm](https://www.w3.org/TR/CSS2/fonts.html#algorithm)).

## 16 Text

Stand: `text-align`, `text-decoration` (underline/overline/line-through), `text-transform`, `letter-spacing`, `white-space` (normal/pre/nowrap/pre-wrap/pre-line), `text-indent` (geparst).

- [ ] **P2 · `word-spacing` fehlt komplett** — Property existiert weder im Parser noch im Renderer ([16.4 Letter and word spacing](https://www.w3.org/TR/CSS2/text.html#spacing-props)).
- [ ] **P2 · `text-indent` wird nicht gerendert** — Wert wird geparst, aber der Renderer liest ihn nicht ([16.1 Indentation](https://www.w3.org/TR/CSS2/text.html#indentation-prop)).
- [ ] **P2 · `text-decoration: blink` fehlt**; Vererbungs-/Propagationsregeln (§16.3.1-Prosa: nicht vererbt, aber über Inline-Boxen propagiert) ungeprüft ([16.3.1 Underlining, overlining, striking, and blinking](https://www.w3.org/TR/CSS2/text.html#lining-striking-props)).

## 17 Tabellen

Stand: `display`-Werte `table`/`inline-table`/`table-row-group`/`table-header-group`/`table-footer-group`/`table-row`/`table-column-group`/`table-column`/`table-cell`/`table-caption`, `border-collapse`, `table-layout` (geparst).

- [ ] **P1 · `caption-side` fehlt** ([17.4.1 Caption position and alignment](https://www.w3.org/TR/CSS2/tables.html#caption-position)).
- [ ] **P1 · `border-spacing` fehlt** — Basis des separierten Rahmenmodells ([17.6.1 The separated borders model](https://www.w3.org/TR/CSS2/tables.html#separated-borders)).
- [ ] **P1 · `empty-cells` fehlt** ([17.6.1.1 Borders and backgrounds around empty cells](https://www.w3.org/TR/CSS2/tables.html#empty-cells)).
- [x] **P3 · Tabellen-Breiten-/Höhen-Algorithmen (Teilmenge) umgesetzt** — `layoutTable` (RenderLayoutEngine) löst die automatische Breite (shrink-to-fit gegen Zellen-Minimum/Maximum, `fitColumns`), spezifizierte Breiten (inkl. `%`; aufgelöst gegen die Containing-Block-Breite — wichtig für BFC-Wurzeln neben Floats) und die Mindesthöhe (`height`-Attribut/CSS = Minimum, Extrahöhe proportional auf die Zeilen verteilt, §17.5.3). Belegt: `floats-wrap-bfc-00{1,3,4,5}` (300px-Soll, LayoutTreeDiffer maxΔPos 0). Offen: `table-layout: fixed`, Spaltenausrichtung, Zeilen-/Zellen-`height`-Hinweise, Caption-Layout ([17.5.2 Table width algorithms](https://www.w3.org/TR/CSS2/tables.html#width-layout), [17.5.3 Table height algorithms](https://www.w3.org/TR/CSS2/tables.html#height-layout)).
- [ ] **P3 · Kollabierendes Rahmenmodell fehlt** — Rahmenkonfliktauflösung und `hidden`-Semantik ([17.6.2 The collapsing border model](https://www.w3.org/TR/CSS2/tables.html#collapsing-borders), [17.6.2.1 Border conflict resolution](https://www.w3.org/TR/CSS2/tables.html#border-conflict-resolution)).
- [ ] **P3 · Anonyme Tabellenobjekte fehlen** — Umschlag-Elemente (`table`/`table-row` um Zellen, die direkt in falschen Eltern stehen) ([17.2.1 Anonymous table objects](https://www.w3.org/TR/CSS2/tables.html#anonymous-boxes)).

## 18 Benutzeroberfläche

Stand: `cursor` (alle CSS2-Keywords), `outline`/`outline-width`/`outline-style`/`outline-color` (Teilmenge), System-Farben (CSS-Color-4-Set).

- [ ] **P1 · `outline-style`: `hidden|groove|ridge|inset|outset` fehlen** — analog `border-style`; nur `none|solid|dotted|dashed|double` ([18.4 Dynamic outlines](https://www.w3.org/TR/CSS2/ui.html#dynamic-outlines)).
- [ ] **P1 · `outline-width`: `thin|medium|thick` fehlen**, `outline-color: invert` fehlt ([18.4 Dynamic outlines](https://www.w3.org/TR/CSS2/ui.html#dynamic-outlines)).
- [ ] **P2 · CSS2-Systemfarben fehlen** — `ActiveBorder`, `Menu`, `Scrollbar`, `ThreeDFace`, `WindowText`, `ButtonFace`, `GrayText` usw. werden nicht akzeptiert; das vorhandene Set (`canvas`, `buttonface`, `field`, …) stammt aus CSS Color 4 ([18.2 System Colors](https://www.w3.org/TR/CSS2/ui.html#system-colors)).
- [ ] **P2 · `cursor: <uri>-Liste fehlt** — eigene Cursor-Bilder mit Fallback-Keyword werden nicht unterstützt ([18.1 Cursors](https://www.w3.org/TR/CSS2/ui.html#cursor-props)).
- [ ] **P2 · System-Schriften (§18.3) fehlen** — siehe `font`-Keywords in Kap. 15 ([18.3 User preferences for fonts](https://www.w3.org/TR/CSS2/ui.html#system-fonts)).

---

## Hinweise zur Reihenfolge (Vorschlag)

1. **Parser-Lücken schließen (P1):** `[att|=val]`, `:lang()`, `@import`, `thin|medium|thick`, fehlende `border-style`-/`outline-style`-Werte, `font-size`-Keywords, `font-family`-Case, `word-spacing`, `caption-side`/`border-spacing`/`empty-cells`, `not`-Auswertung.
2. **Eigenschaften ergänzen (P2):** `quotes`, Zähler, `list-style-position`/`-image`, `unicode-bidi`, Systemfarben/-schriften, `cursor: url()`.
3. **Layout-/Render-Arbeit (P3):** Margin-Kollaps, §10.3/§10.6-Algorithmen, Tabellen-Layout, kollabierende Rahmen, `clip`, `:first-line`/`:first-letter`, Seitenmedien.
