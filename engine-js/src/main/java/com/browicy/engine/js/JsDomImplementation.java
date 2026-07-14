package com.browicy.engine.js;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import com.browicy.engine.dom.DocumentType;
import com.browicy.engine.dom.DomImplementation;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class JsDomImplementation implements ProxyObject {
    private static final List<String> MEMBERS =
            List.of("createDocument", "createDocumentType", "createHTMLDocument");

    private final DomImplementation implementation = new DomImplementation();
    private final JsDocument owner;

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "createDocument" -> owner.domOperation((ProxyExecutable) args -> {
                String namespace = nullableString(args, 0);
                String qualifiedName = nullableString(args, 1);
                JsNodeLike type = JsElement.expectNode(args, 2, true);
                if (type != null && !(type.unwrapNode() instanceof DocumentType)) {
                    throw new IllegalArgumentException("Es wird ein DocumentType erwartet");
                }
                JsDocument document = owner.wrapDocument(implementation.createDocument(
                        namespace, qualifiedName, type == null ? null : (DocumentType) type.unwrapNode()));
                if (type != null) {
                    document.preserveWrapper(type);
                }
                return document;
            });
            case "createDocumentType" -> owner.domOperation((ProxyExecutable) args -> owner.wrap(
                    implementation.createDocumentType(string(args, 0), string(args, 1), string(args, 2))));
            case "createHTMLDocument" -> owner.domOperation((ProxyExecutable) args ->
                    owner.wrapDocument(implementation.createHTMLDocument(
                            args.length == 0 ? "" : string(args, 0))));
            default -> null;
        };
    }

    @Override public Object getMemberKeys() { return MEMBERS.toArray(); }
    @Override public boolean hasMember(String key) { return MEMBERS.contains(key); }
    @Override public void putMember(String key, Value value) { throw new UnsupportedOperationException(key); }

    private static String nullableString(Value[] args, int index) {
        return index >= args.length || args[index].isNull() ? null : string(args, index);
    }

    private static String string(Value[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Argument " + index + " fehlt");
        }
        return args[index].isString() ? args[index].asString() : args[index].toString();
    }
}
