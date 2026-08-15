package com.browicy.engine.js;

import com.browicy.engine.dom.DocumentFragment;
import com.browicy.engine.dom.Element;
import com.browicy.engine.js.handlers.JsAttributeHandler;
import com.browicy.engine.js.handlers.JsEmbeddedContentHandler;
import com.browicy.engine.js.handlers.JsEventHandler;
import com.browicy.engine.js.handlers.JsFormHandler;
import com.browicy.engine.js.handlers.JsGeometryHandler;
import com.browicy.engine.js.handlers.JsInteractiveHandler;
import com.browicy.engine.js.handlers.JsMemberHandler;
import com.browicy.engine.js.handlers.JsMutationHandler;
import com.browicy.engine.js.handlers.JsNodeConstants;
import com.browicy.engine.js.handlers.JsNodeHandler;
import com.browicy.engine.js.handlers.JsQueryHandler;
import com.browicy.engine.js.handlers.JsTableHandler;
import com.browicy.engine.js.handlers.JsUrlHandler;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class JsElement implements ProxyObject, JsNodeLike {

    private static final List<JsMemberHandler> HANDLERS = List.of(
            new JsFormHandler(), new JsTableHandler(), new JsUrlHandler(),
            new JsGeometryHandler(), new JsAttributeHandler(), new JsNodeHandler(),
            new JsQueryHandler(), new JsMutationHandler(), new JsEmbeddedContentHandler(),
            new JsInteractiveHandler(), new JsEventHandler());

    private static final List<String> MEMBERS = buildMembers();

    private static List<String> buildMembers() {
        List<String> keys = new ArrayList<>(JsNodeConstants.keys());
        for (JsMemberHandler handler : HANDLERS) {
            keys.addAll(handler.keys());
        }
        return List.copyOf(keys);
    }

    private final Element element;
    private final JsDocument document;
    private JsDomTokenList classList;
    private JsDomStringMap dataset;
    private JsStyleDeclaration style;
    private JsCssStyleSheet sheet;
    private final Map<String, Value> expandos = new LinkedHashMap<>();
    private DocumentFragment contentFragment;
    private String embeddedDocumentSource;
    private JsDocument embeddedDocument;

    public Element unwrap() {
        return element;
    }

    @Override
    public Element unwrapNode() {
        return element;
    }

    @Override
    public Object getMember(String key) {
        Integer constant = JsNodeConstants.valueOf(key);
        if (constant != null) {
            return constant;
        }
        for (JsMemberHandler handler : HANDLERS) {
            if (handler.canHandle(key)) {
                return handler.get(key, this, document);
            }
        }
        return expandos.get(key);
    }

    @Override
    public void putMember(String key, Value value) {
        for (JsMemberHandler handler : HANDLERS) {
            if (handler.canHandle(key)) {
                if (handler.set(key, value, this, document)) {
                    return;
                }
                break;
            }
        }
        Value previous = expandos.put(key, value);
        if (key.length() > 2 && key.startsWith("on")) {
            String eventType = key.substring(2).toLowerCase(Locale.ROOT);
            if (previous != null && previous.canExecute()) {
                document.removeEventListener(element, eventType, previous, false);
            }
            if (!value.isNull() && value.canExecute()) {
                document.addEventListener(element, eventType, value, false);
            }
        }
    }

    @Override
    public Object getMemberKeys() {
        List<String> keys = new ArrayList<>(MEMBERS);
        keys.addAll(expandos.keySet());
        return ProxyArray.fromArray(keys.toArray());
    }

    @Override
    public boolean hasMember(String key) {
        return MEMBERS.contains(key) || expandos.containsKey(key);
    }

    public JsDomTokenList classList() {
        return classList == null
                ? classList = new JsDomTokenList(element.getClassList(), document) : classList;
    }

    public JsDomStringMap dataset() {
        return dataset == null ? dataset = new JsDomStringMap(element) : dataset;
    }

    public JsStyleDeclaration style() {
        return style == null ? style = new JsStyleDeclaration(element) : style;
    }

    public JsCssStyleSheet sheet() {
        return sheet == null ? sheet = document.styleSheet(element) : sheet;
    }

    public void styleContentMaybeChanged() {
        if ("style".equals(element.getTagName().toLowerCase(Locale.ROOT))) {
            document.styleSheetContentChanged(element);
        }
    }

    public DocumentFragment contentFragment() {
        return contentFragment;
    }

    public void setContentFragment(DocumentFragment fragment) {
        contentFragment = fragment;
    }

    public JsDocument embeddedDocument() {
        return embeddedDocument;
    }

    public String embeddedDocumentSource() {
        return embeddedDocumentSource;
    }

    public void setEmbeddedDocument(JsDocument document) {
        embeddedDocument = document;
    }

    public void setEmbeddedDocumentSource(String source) {
        embeddedDocumentSource = source;
    }

    public static JsNodeLike expectNode(Value[] args, int index, boolean nullable) {
        return JsMemberHandler.expectNode(args, index, nullable);
    }

    @Override
    public String toString() {
        return "[object HTML" + element.getTagName() + "Element]";
    }
}
