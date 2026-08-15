package com.browicy.css21;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LayoutTreeDiffer {

    private static final int VIEWPORT_WIDTH = 800;
    private static final int VIEWPORT_HEIGHT = 600;

    private LayoutTreeDiffer() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args[0] == null || args[0].isBlank()) {
            System.err.println("Verwendung: LayoutTreeDiffer <test-pfad> [--json] [--out <datei>]");
            System.err.println("  <test-pfad>  relativer Pfad innerhalb der Suite, z. B. floats/floats-rule7-outside-left-001.xht");
            System.err.println("  --json       JSON-Ausgabe statt Tabelle");
            System.err.println("  --out <f>    Ergebnis zusätzlich in Datei schreiben (layout-diff.txt bzw. .json)");
            System.exit(2);
        }

        String testPath = args[0];
        boolean asJson = false;
        Path output = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--json" -> asJson = true;
                case "--out" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("--out erwartet einen Dateipfad");
                        System.exit(2);
                    }
                    output = Path.of(args[++i]);
                }
                default -> {
                    System.err.println("Unbekanntes Argument: " + args[i]);
                    System.exit(2);
                }
            }
        }

        int exitCode = 1;
        try (Css21TestServer server = Css21TestServer.start();
             ChromeReferenceRenderer chrome = new ChromeReferenceRenderer();
             BrowicyRenderer browicy = new BrowicyRenderer()) {

            String url = server.url(testPath);
            List<ElementGeometrySnapshot> chromeGeometry =
                    chrome.extractGeometry(url, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
            List<ElementGeometrySnapshot> browicyGeometry =
                    browicy.extractGeometry(url, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);

            LayoutTreeComparator.ComparisonResult result =
                    LayoutTreeComparator.compare(chromeGeometry, browicyGeometry);

            String text = LayoutTreeComparator.formatTable(result);
            String json = LayoutTreeComparator.toJson(result);

            if (output != null) {
                Files.writeString(output, asJson ? json : text, java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("Ergebnis geschrieben nach: " + output.toAbsolutePath());
            }

            if (asJson) {
                System.out.println(json);
            } else {
                System.out.print(text);
            }

            exitCode = result.passed() ? 0 : 1;
        } catch (IOException e) {
            System.err.println("Fehler beim Lesen/Schreiben: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Fehler beim Layout-Vergleich: " + e.getMessage());
            e.printStackTrace();
        }
        System.exit(exitCode);
    }
}
