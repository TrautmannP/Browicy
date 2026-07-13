package com.browicy.engine.html;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Node;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DocumentResourceScanner {

    private static final Set<String> CLASSIC_SCRIPT_TYPES = Set.of(
            "", "text/javascript", "application/javascript", "application/ecmascript",
            "text/ecmascript");

    public DocumentResources scan(Document document) {
        List<StyleSheetResource> styleSheets = new ArrayList<>();
        List<ScriptResource> scripts = new ArrayList<>();
        ScanState state = new ScanState();
        scanNode(document, document, false, styleSheets, scripts, state);
        return new DocumentResources(styleSheets, scripts);
    }

    private static void scanNode(Node node,
                                 Document document,
                                 boolean insideHead,
                                 List<StyleSheetResource> styleSheets,
                                 List<ScriptResource> scripts,
                                 ScanState state) {
        for (Node child : node.getChildren()) {
            boolean childInsideHead = insideHead;
            if (child instanceof Element element) {
                childInsideHead |= "head".equals(element.getTagName());
                scanElement(element, document, childInsideHead, styleSheets, scripts, state);
            }
            scanNode(child, document, childInsideHead, styleSheets, scripts, state);
        }
    }

    private static void scanElement(Element element,
                                    Document document,
                                    boolean insideHead,
                                    List<StyleSheetResource> styleSheets,
                                    List<ScriptResource> scripts,
                                    ScanState state) {
        switch (element.getTagName()) {
            case "style" -> styleSheets.add(new StyleSheetResource.Inline(
                    state.nextStyleOrder++, element, element.getTextContent()));
            case "link" -> externalStyleSheet(element, document, insideHead, state)
                    .ifPresent(styleSheets::add);
            case "script" -> script(element, document, state).ifPresent(scripts::add);
            default -> {
            }
        }
    }

    private static java.util.Optional<StyleSheetResource.External> externalStyleSheet(
            Element element, Document document, boolean insideHead, ScanState state) {
        if (!hasRelToken(element.getAttribute("rel"), "stylesheet")
                || !element.hasAttribute("href")
                || element.hasAttribute("disabled")) {
            return java.util.Optional.empty();
        }
        int sourceOrder = state.nextStyleOrder++;
        return resolveHttpUri(document, element.getAttribute("href"))
                .map(uri -> new StyleSheetResource.External(
                        sourceOrder, element, uri, insideHead));
    }

    private static java.util.Optional<ScriptResource> script(
            Element element, Document document, ScanState state) {
        boolean module = "module".equalsIgnoreCase(element.getAttribute("type"));
        if (!module && !isClassicScript(element)) {
            return java.util.Optional.empty();
        }
        int treeOrder = state.nextScriptOrder++;
        if (element.hasAttribute("src")) {
            return resolveHttpUri(document, element.getAttribute("src"))
                    .map(uri -> new ScriptResource.External(
                            treeOrder, element, uri, element.hasAttribute("async"), module));
        }
        return java.util.Optional.of(new ScriptResource.Inline(
                treeOrder, element, element.getTextContent(), module));
    }

    private static boolean isClassicScript(Element element) {
        String type = element.getAttribute("type");
        if (type == null) {
            return true;
        }
        return CLASSIC_SCRIPT_TYPES.contains(type.strip().toLowerCase(Locale.ROOT));
    }

    private static boolean hasRelToken(String rel, String expected) {
        if (rel == null || rel.isBlank()) {
            return false;
        }
        for (String token : rel.strip().split("[\\t\\n\\f\\r ]+")) {
            if (token.equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

    private static java.util.Optional<URI> resolveHttpUri(Document document, String reference) {
        if (reference == null || reference.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            URI resolved = document.getBaseUri().resolve(reference.strip());
            String scheme = resolved.getScheme();
            if (scheme == null || resolved.getHost() == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(withoutFragment(resolved));
        } catch (IllegalArgumentException | URISyntaxException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static URI withoutFragment(URI uri) throws URISyntaxException {
        return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                uri.getPath(), uri.getQuery(), null);
    }

    private static final class ScanState {
        private int nextStyleOrder;
        private int nextScriptOrder;
    }
}
