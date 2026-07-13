# Acid3 tests

Dieses optionale Maven-Modul fuehrt die 100 JavaScript-Untertests der originalen
Acid3-Testseite gegen die Browicy-Engine aus. Jeder Untertest erscheint als eigener
parametrisierter JUnit-Test. Acid3 wird trotzdem nur einmal und in Originalreihenfolge
ausgefuehrt, weil mehrere Untertests absichtlich auf dem Zustand ihrer Vorgaenger
aufbauen.

Das Modul gehoert nicht zum Standard-Reaktor. Dadurch bleibt der normale Build gruen,
solange Browicy noch nicht alle Acid3-Funktionen implementiert hat.

```bat
# Acid3 inklusive benoetigter Module bauen und ausfuehren
mvn-graal.cmd -Pacid3 -pl acid3-tests -am test
```

Die eingecheckte Testseite stammt aus dem Web Platform Tests Repository. Ihre
Revision und Lizenzhinweise stehen unter `src/test/resources/acid3/UPSTREAM.md`.
Der Harness ersetzt zur Laufzeit ausschliesslich die drei `postMessage`-Meldungen
des Acid3-Runners durch maschinenlesbare `console.log`-Ausgaben. Die Tests selbst
werden nicht veraendert.
