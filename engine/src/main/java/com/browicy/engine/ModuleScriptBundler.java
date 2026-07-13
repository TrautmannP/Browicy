package com.browicy.engine;

import com.browicy.engine.net.NetworkResourceType;
import com.browicy.engine.net.SubResourceLoad;
import com.browicy.engine.net.SubResourceLoader;
import com.browicy.engine.net.TextResource;
import com.browicy.engine.net.ResourceLoad;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ModuleScriptBundler {

    private static final Pattern DEFAULT_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+([A-Za-z_$][\\w$]*)\\s+from\\s+(['\"])([^'\"]+)\\2\\s*;?");
    private static final Pattern ANY_IMPORT_EXPORT = Pattern.compile(
            "(?m)^\\s*(?:import\\s|export\\s+(?!default\\b))");

    private final SubResourceLoader loader;
    private final List<ResourceLoad> loads;

    ModuleScriptBundler(SubResourceLoader loader, List<ResourceLoad> loads) {
        this.loader = loader;
        this.loads = loads;
    }

    String bundle(URI sourceUri, String source) throws IOException, InterruptedException {
        return "(" + moduleExpression(sourceUri, source, new HashSet<>()) + ");";
    }

    private String moduleExpression(URI sourceUri, String source, Set<URI> stack)
            throws IOException, InterruptedException {
        if (!stack.add(sourceUri)) {
            throw new IOException("Zyklische ES-Modul-Abhängigkeit: " + sourceUri);
        }
        try {
            StringBuilder declarations = new StringBuilder();
            Matcher imports = DEFAULT_IMPORT.matcher(source);
            List<ImportLoad> importLoads = new ArrayList<>();
            while (imports.find()) {
                URI dependencyUri = resolve(sourceUri, imports.group(3));
                SubResourceLoad load = loader.loadAsync(dependencyUri, NetworkResourceType.SCRIPT);
                loads.add(load);
                importLoads.add(new ImportLoad(imports.group(1), load));
            }
            imports.reset();
            StringBuffer body = new StringBuffer();
            int importIndex = 0;
            while (imports.find()) {
                ImportLoad importLoad = importLoads.get(importIndex++);
                TextResource dependency = importLoad.load().await();
                declarations.append("const ").append(importLoad.localName()).append(" = (")
                        .append(moduleExpression(dependency.uri(), dependency.content(), stack))
                        .append(").default;\n");
                imports.appendReplacement(body, "");
            }
            imports.appendTail(body);
            String transformed = body.toString().replaceFirst(
                    "(?m)^\\s*export\\s+default\\s+", "__exports.default = ");
            if (ANY_IMPORT_EXPORT.matcher(transformed).find()) {
                throw new IOException("Noch nicht unterstützte ES-Modul-Syntax in " + sourceUri);
            }
            return "(() => { const __exports = {};\n" + declarations + transformed
                    + "\nreturn __exports; })()";
        } finally {
            stack.remove(sourceUri);
        }
    }

    private static URI resolve(URI base, String reference) throws IOException {
        URI resolved;
        try {
            resolved = base.resolve(reference);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Ungültiger ES-Modul-Import: " + reference, failure);
        }
        String scheme = resolved.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IOException("Nur HTTP(S)-ES-Module werden unterstützt: " + resolved);
        }
        return resolved;
    }

    private record ImportLoad(String localName, SubResourceLoad load) { }
}
