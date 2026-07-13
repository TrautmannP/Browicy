package com.browicy.engine.dom;

import lombok.Getter;

@Getter
public final class DomException extends RuntimeException {

    public static final int HIERARCHY_REQUEST_ERR = 3;
    public static final int WRONG_DOCUMENT_ERR = 4;
    public static final int INVALID_CHARACTER_ERR = 5;
    public static final int NOT_FOUND_ERR = 8;
    public static final int INVALID_STATE_ERR = 11;
    public static final int NAMESPACE_ERR = 14;
    public static final int INVALID_NODE_TYPE_ERR = 24;

    private final String domName;
    private final int code;

    private DomException(String domName, int code, String message) {
        super("DOM_EXCEPTION|" + domName + "|" + code + "|" + message);
        this.domName = domName;
        this.code = code;
    }

    public static DomException hierarchyRequest(String message) {
        return new DomException("HierarchyRequestError", HIERARCHY_REQUEST_ERR, message);
    }

    public static DomException notFound(String message) {
        return new DomException("NotFoundError", NOT_FOUND_ERR, message);
    }

    public static DomException wrongDocument(String message) {
        return new DomException("WrongDocumentError", WRONG_DOCUMENT_ERR, message);
    }

    public static DomException invalidCharacter(String message) {
        return new DomException("InvalidCharacterError", INVALID_CHARACTER_ERR, message);
    }

    public static DomException namespace(String message) {
        return new DomException("NamespaceError", NAMESPACE_ERR, message);
    }

    public static DomException invalidState(String message) {
        return new DomException("InvalidStateError", INVALID_STATE_ERR, message);
    }

    public static DomException invalidNodeType(String message) {
        return new DomException("InvalidNodeTypeError", INVALID_NODE_TYPE_ERR, message);
    }
}
