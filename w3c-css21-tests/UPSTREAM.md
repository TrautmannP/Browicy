# Upstream: W3C CSS 2.1 Test Suite

Die Testsuite wird **nicht eingecheckt** (55 MB). Der Harness lädt sie beim
ersten Lauf von der gepinnten Upstream-Revision und cached sie unter
`~/.browicy/w3c-css21-suite/<sha>/`.

## Quelle und Pin

- Repository: https://github.com/w3c/csswg-test (archiviert, offizielles
  CSSWG-Test-Repository)
- Branch: `master`
- Gepinnte Revision: `8eced53cb246ba1ab8b9450e36d2d57dc74a1f4a`
- Archiv-URL: `https://github.com/w3c/csswg-test/archive/<sha>.zip`
  (unveränderlich; 302 → codeload)
- Inhalt: komplettes `css21/`-Verzeichnis (13 401 Dateien, ca. 55 MB)

Die ursprüngliche Veröffentlichung der Suite („CSS 2.1 Conformance Test
Suite", Snapshot 2011-03-23, genutzt für die W3C Recommendation vom
2011-06-07) war unter https://www.w3.org/Style/CSS/Test/CSS2.1/20110323/
verlinkt. Die dort referenzierten Testdateien lagen auf dem inzwischen
abgeschalteten Host `test.csswg.org` (Umleitung auf wiki.csswg.org, kein
Snapshot im Wayback-Archiv). Der Nachfolge-Bestand im CSSWG-Repository
enthält dieselbe Suite im WPT-Format (XHTML-`.xht`-Tests mit
`-ref`-Referenzdateien und `support/`-Ressourcen) und wird hier unverändert
verwendet.

## Lizenz

Im heruntergeladenen `css21/`-Verzeichnis liegen die Lizenztexte des
Upstreams: `LICENSE-BSD`, `LICENSE-W3CD`, `LICENSE-W3CTS`.

## Pin ändern

`Css21Suite.UPSTREAM_SHA` anpassen → der nächste Lauf lädt die neue Revision
und legt einen frischen Cache-Ordner an (alte Caches bleiben liegen).
Lizenztexte der neuen Revision prüfen.
