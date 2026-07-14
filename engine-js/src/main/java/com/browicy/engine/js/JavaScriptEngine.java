package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentReadyState;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JavaScriptEngine {

    public static final long DEFAULT_STATEMENT_LIMIT = 10_000_000;

    static final String BROWSER_BOOTSTRAP = """
            globalThis.window = globalThis;
            globalThis.self = globalThis;
            globalThis.onload = null;
            globalThis.Node = function Node() { throw new TypeError('Illegal constructor'); };
            Object.defineProperty(Node, Symbol.hasInstance, {
              value: candidate => candidate != null && typeof candidate.nodeType === 'number'
            });
            Node.ELEMENT_NODE = 1;
            Node.TEXT_NODE = 3;
            Node.COMMENT_NODE = 8;
            Node.DOCUMENT_NODE = 9;
            Node.DOCUMENT_TYPE_NODE = 10;
            Node.DOCUMENT_FRAGMENT_NODE = 11;
            Node.DOCUMENT_POSITION_DISCONNECTED = 0x01;
            Node.DOCUMENT_POSITION_PRECEDING = 0x02;
            Node.DOCUMENT_POSITION_FOLLOWING = 0x04;
            Node.DOCUMENT_POSITION_CONTAINS = 0x08;
            Node.DOCUMENT_POSITION_CONTAINED_BY = 0x10;
            Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC = 0x20;
            globalThis.EventTarget = function EventTarget() { throw new TypeError('Illegal constructor'); };
            Object.defineProperty(EventTarget, Symbol.hasInstance, {
              value: candidate => candidate != null && typeof candidate.addEventListener === 'function'
            });
            globalThis.HTMLElement = function HTMLElement() { throw new TypeError('Illegal constructor'); };
            HTMLElement.prototype = Object.create(Node.prototype);
            Object.defineProperty(HTMLElement, Symbol.hasInstance, {
              value: candidate => candidate != null && candidate.nodeType === 1
            });
            globalThis.Window = function Window() { throw new TypeError('Illegal constructor'); };
            Object.defineProperty(Window, Symbol.hasInstance, { value: candidate => candidate === window });
            const __windowListeners = new Map();
            globalThis.addEventListener = (type, callback) => {
              type = String(type);
              const listeners = __windowListeners.get(type) || [];
              if (typeof callback === 'function' && !listeners.includes(callback)) listeners.push(callback);
              __windowListeners.set(type, listeners);
            };
            globalThis.removeEventListener = (type, callback) => {
              const listeners = __windowListeners.get(String(type)) || [];
              __windowListeners.set(String(type), listeners.filter(candidate => candidate !== callback));
            };
            globalThis.__browicyDispatchWindowEvent = (type, event) => {
              for (const listener of [...(__windowListeners.get(String(type)) || [])]) listener.call(window, event);
            };
            globalThis.CSS = Object.freeze({ supports: (...args) => __browicyCssSupports(...args) });
            globalThis.getComputedStyle = element => __browicyGetComputedStyle(element);
            const __locationHref = String(document.URL || 'about:blank');
            const __queryStart = __locationHref.indexOf('?');
            globalThis.location = Object.freeze({
              href: __locationHref,
              search: __queryStart < 0 ? '' : __locationHref.substring(__queryStart).split('#')[0]
            });
            globalThis.URLSearchParams = class URLSearchParams {
              constructor(source = '') {
                this.values = Object.create(null);
                String(source).replace(/^\\?/, '').split('&').filter(Boolean).forEach(entry => {
                  const parts = entry.split('=');
                  this.values[decodeURIComponent(parts.shift())] = decodeURIComponent(parts.join('=') || '');
                });
              }
              get(name) { return Object.prototype.hasOwnProperty.call(this.values, name) ? this.values[name] : null; }
            };
            const __storage = new Map();
            globalThis.localStorage = Object.freeze({
              getItem: key => __storage.has(String(key)) ? __storage.get(String(key)) : null,
              setItem: (key, value) => __storage.set(String(key), String(value)),
              removeItem: key => __storage.delete(String(key)),
              clear: () => __storage.clear()
            });
            globalThis.history = Object.freeze({ replaceState: () => undefined });
            globalThis.matchMedia = query => Object.freeze({
              media: String(query), matches: false,
              addEventListener: () => undefined, removeEventListener: () => undefined
            });
            globalThis.Range = function Range() { return document.createRange(); };
            Range.START_TO_START = 0;
            Range.START_TO_END = 1;
            Range.END_TO_END = 2;
            Range.END_TO_START = 3;
            globalThis.DOMException = class DOMException extends Error {
              constructor(message = '', name = 'Error') {
                super(String(message));
                this.name = String(name);
                this.code = DOMException[name] || 0;
              }
            };
            Object.assign(DOMException, {
              INDEX_SIZE_ERR:1, DOMSTRING_SIZE_ERR:2, HIERARCHY_REQUEST_ERR:3,
              WRONG_DOCUMENT_ERR:4, INVALID_CHARACTER_ERR:5, NO_DATA_ALLOWED_ERR:6,
              NO_MODIFICATION_ALLOWED_ERR:7, NOT_FOUND_ERR:8, NOT_SUPPORTED_ERR:9,
              INUSE_ATTRIBUTE_ERR:10, INVALID_STATE_ERR:11, SYNTAX_ERR:12,
              INVALID_MODIFICATION_ERR:13, NAMESPACE_ERR:14, INVALID_ACCESS_ERR:15,
              VALIDATION_ERR:16, TYPE_MISMATCH_ERR:17, SECURITY_ERR:18,
              NETWORK_ERR:19, ABORT_ERR:20, URL_MISMATCH_ERR:21, QUOTA_EXCEEDED_ERR:22,
              TIMEOUT_ERR:23, INVALID_NODE_TYPE_ERR:24, DATA_CLONE_ERR:25,
               HierarchyRequestError:3, WrongDocumentError:4, InvalidCharacterError:5,
               NotFoundError:8, InvalidStateError:11, SyntaxError:12, NamespaceError:14, InvalidNodeTypeError:24
            });
            Object.assign(DOMException.prototype, DOMException);
            globalThis.NodeFilter = Object.freeze({
              FILTER_ACCEPT:1,FILTER_REJECT:2,FILTER_SKIP:3,
              SHOW_ALL:0xFFFFFFFF,SHOW_ELEMENT:1,SHOW_ATTRIBUTE:2,SHOW_TEXT:4,
              SHOW_CDATA_SECTION:8,SHOW_ENTITY_REFERENCE:16,SHOW_ENTITY:32,
              SHOW_PROCESSING_INSTRUCTION:64,SHOW_COMMENT:128,SHOW_DOCUMENT:256,
              SHOW_DOCUMENT_TYPE:512,SHOW_DOCUMENT_FRAGMENT:1024,SHOW_NOTATION:2048
            });
            globalThis.Event = function Event(type, init) {
              init = init || {};
              const event = document.createEvent('Event');
              event.initEvent(String(type), Boolean(init.bubbles), Boolean(init.cancelable));
              return event;
            };
            Event.NONE = 0;
            Event.CAPTURING_PHASE = 1;
            Event.AT_TARGET = 2;
            Event.BUBBLING_PHASE = 3;
            globalThis.UIEvent = function UIEvent(type, init) {
              init = init || {};
              const event = document.createEvent('UIEvent');
              event.initUIEvent(String(type), Boolean(init.bubbles), Boolean(init.cancelable),
                                init.view == null ? null : init.view, Number(init.detail) || 0);
              return event;
            };
            UIEvent.NONE = Event.NONE;
            UIEvent.CAPTURING_PHASE = Event.CAPTURING_PHASE;
            UIEvent.AT_TARGET = Event.AT_TARGET;
            UIEvent.BUBBLING_PHASE = Event.BUBBLING_PHASE;
            const __browicyMutationObservers = new Map();
            const __browicyMutationObserverState = new WeakMap();
            let __browicyNextMutationObserverId = 0;
            const __browicyMutationObserverData = observer => {
              const state = __browicyMutationObserverState.get(observer);
              if (state == null) throw new TypeError('Illegal invocation');
              return state;
            };
            const __browicyMutationRecords = records => Array.from(records, record => Object.freeze({
              type: String(record.type),
              target: record.target,
              addedNodes: Object.freeze(Array.from(record.addedNodes)),
              removedNodes: Object.freeze(Array.from(record.removedNodes)),
              previousSibling: record.previousSibling,
              nextSibling: record.nextSibling,
              attributeName: record.attributeName,
              attributeNamespace: record.attributeNamespace,
              oldValue: record.oldValue
            }));
            globalThis.MutationObserver = class MutationObserver {
              constructor(callback) {
                if (typeof callback !== 'function') {
                  throw new TypeError("MutationObserver: callback must be a function");
                }
                const id = ++__browicyNextMutationObserverId;
                __browicyMutationObserverState.set(this, { id: id, callback: callback });
                __browicyMutationObservers.set(id, this);
              }
              observe(target, options) {
                const state = __browicyMutationObserverData(this);
                if (!(target instanceof Node)) {
                  throw new TypeError("MutationObserver.observe: target must be a Node");
                }
                if (options == null || typeof options !== 'object') {
                  throw new TypeError("MutationObserver.observe: options must be an object");
                }
                const childList = Boolean(options.childList);
                const attributesSpecified = options.attributes !== undefined;
                const attributeOldValue = Boolean(options.attributeOldValue);
                const attributeFilterSpecified = options.attributeFilter !== undefined;
                const attributeFilter = attributeFilterSpecified
                    ? Array.from(options.attributeFilter, value => String(value)) : [];
                const attributes = attributesSpecified ? Boolean(options.attributes)
                    : attributeOldValue || attributeFilterSpecified;
                const characterDataSpecified = options.characterData !== undefined;
                const characterDataOldValue = Boolean(options.characterDataOldValue);
                const characterData = characterDataSpecified ? Boolean(options.characterData)
                    : characterDataOldValue;
                const subtree = Boolean(options.subtree);
                if (!childList && !attributes && !characterData) {
                  throw new TypeError("MutationObserver.observe: no mutation type selected");
                }
                if (!attributes && (attributeOldValue || attributeFilterSpecified)) {
                  throw new TypeError("MutationObserver.observe: attributes is false");
                }
                if (!characterData && characterDataOldValue) {
                  throw new TypeError("MutationObserver.observe: characterData is false");
                }
                __browicyMutationObserve(state.id, target, childList, attributes,
                    characterData, subtree, attributeOldValue, characterDataOldValue,
                    attributeFilter);
              }
              disconnect() {
                __browicyMutationDisconnect(__browicyMutationObserverData(this).id);
              }
              takeRecords() {
                return __browicyMutationRecords(
                    __browicyMutationTakeRecords(__browicyMutationObserverData(this).id));
              }
            };
            globalThis.__browicyDeliverMutationObserver = (id, records) => {
              const observer = __browicyMutationObservers.get(Number(id));
              if (observer == null || records.length === 0) return;
              __browicyMutationObserverData(observer).callback.call(
                  observer, __browicyMutationRecords(records), observer);
            };
            """;

    static final String FETCH_BOOTSTRAP = """
            (() => {
              'use strict';
              class Headers {
                constructor(init) {
                  this._entries = [];
                  if (init == null) return;
                  if (init instanceof Headers) {
                    for (const entry of init._entries) this.append(entry[0], entry[1]);
                  } else if (Array.isArray(init)) {
                    for (const pair of init) this.append(pair[0], pair[1]);
                  } else if (typeof init === 'object') {
                    for (const name of Object.keys(init)) this.append(name, init[name]);
                  } else {
                    throw new TypeError('Ungültige Headers-Initialisierung');
                  }
                }
                append(name, value) {
                  this._entries.push([String(name).toLowerCase(), String(value).trim()]);
                }
                set(name, value) { this.delete(name); this.append(name, value); }
                delete(name) {
                  name = String(name).toLowerCase();
                  this._entries = this._entries.filter(entry => entry[0] !== name);
                }
                get(name) {
                  name = String(name).toLowerCase();
                  const values = [];
                  for (const entry of this._entries) if (entry[0] === name) values.push(entry[1]);
                  return values.length === 0 ? null : values.join(', ');
                }
                has(name) { return this.get(name) !== null; }
                forEach(callback, thisArg) {
                  for (const entry of this.entries()) callback.call(thisArg, entry[1], entry[0], this);
                }
                *entries() {
                  const sorted = [...this._entries].sort((a, b) =>
                      a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0);
                  for (const entry of sorted) yield [entry[0], entry[1]];
                }
                *keys() { for (const entry of this.entries()) yield entry[0]; }
                *values() { for (const entry of this.entries()) yield entry[1]; }
                [Symbol.iterator]() { return this.entries(); }
              }
              class Response {
                constructor(body, init) {
                  init = init || {};
                  this._bodyText = body == null ? '' : String(body);
                  this._bodyUsed = false;
                  this.status = init.status === undefined ? 200 : Number(init.status);
                  this.statusText = init.statusText === undefined ? '' : String(init.statusText);
                  this.headers = init.headers instanceof Headers
                      ? init.headers : new Headers(init.headers);
                  this.url = '';
                  this.type = 'basic';
                  this.redirected = false;
                }
                get ok() { return this.status >= 200 && this.status <= 299; }
                get bodyUsed() { return this._bodyUsed; }
                _consume() {
                  if (this._bodyUsed) {
                    return Promise.reject(new TypeError('Response-Body wurde bereits gelesen'));
                  }
                  this._bodyUsed = true;
                  return Promise.resolve(this._bodyText);
                }
                text() { return this._consume(); }
                json() { return this._consume().then(text => JSON.parse(text)); }
                clone() {
                  if (this._bodyUsed) throw new TypeError('Response-Body wurde bereits gelesen');
                  const copy = new Response(this._bodyText, {
                    status: this.status, statusText: this.statusText,
                    headers: new Headers(this.headers)
                  });
                  copy.url = this.url;
                  copy.redirected = this.redirected;
                  return copy;
                }
              }
              globalThis.Headers = Headers;
              globalThis.Response = Response;
              globalThis.fetch = function fetch(input, init) {
                return new Promise((resolve, reject) => {
                  let url;
                  let method = 'GET';
                  let hasBody = false;
                  try {
                    url = String(input !== null && typeof input === 'object'
                        && input.url !== undefined ? input.url : input);
                    if (init != null) {
                      if (init.method != null) method = String(init.method).toUpperCase();
                      hasBody = init.body != null;
                    }
                  } catch (error) {
                    reject(new TypeError('fetch: ' + String(error && error.message || error)));
                    return;
                  }
                  if (method !== 'GET') {
                    reject(new TypeError(
                        'fetch: Methode ' + method + ' wird noch nicht unterstützt'));
                    return;
                  }
                  if (hasBody) {
                    reject(new TypeError('fetch: Ein Request-Body wird noch nicht unterstützt'));
                    return;
                  }
                  __browicyFetch(url,
                      (finalUrl, status, statusText, headerPairs, bodyText) => {
                        try {
                          const headers = new Headers();
                          for (let i = 0; i + 1 < headerPairs.length; i += 2) {
                            headers.append(headerPairs[i], headerPairs[i + 1]);
                          }
                          const response = new Response(bodyText,
                              { status: status, statusText: statusText, headers: headers });
                          response.url = String(finalUrl);
                          resolve(response);
                        } catch (error) {
                          reject(error);
                        }
                      },
                      message => reject(new TypeError(String(message))));
                });
              };
            })();
            """;

    static final String XHR_BOOTSTRAP = """
            (() => {
              'use strict';
              const UNSENT = 0, OPENED = 1, HEADERS_RECEIVED = 2, LOADING = 3, DONE = 4;
              class XMLHttpRequest {
                constructor() {
                  this._listeners = new Map();
                  this._generation = 0;
                  this._requestHeaders = [];
                  this._responseType = '';
                  this._reset();
                  this.timeout = 0;
                  this.withCredentials = false;
                  this.upload = Object.freeze({
                    addEventListener: () => undefined,
                    removeEventListener: () => undefined,
                    dispatchEvent: () => true
                  });
                  this.onreadystatechange = null;
                  this.onloadstart = null;
                  this.onprogress = null;
                  this.onload = null;
                  this.onerror = null;
                  this.onabort = null;
                  this.ontimeout = null;
                  this.onloadend = null;
                }
                _reset() {
                  this._state = UNSENT;
                  this._sent = false;
                  this._status = 0;
                  this._statusText = '';
                  this._responseText = '';
                  this._responseUrl = '';
                  this._headers = [];
                }
                get readyState() { return this._state; }
                get status() { return this._status; }
                get statusText() { return this._statusText; }
                get responseURL() { return this._responseUrl; }
                get responseXML() { return null; }
                get responseType() { return this._responseType; }
                set responseType(value) {
                  value = String(value);
                  if (value === '' || value === 'text' || value === 'json') {
                    this._responseType = value;
                  }
                }
                get responseText() {
                  if (this._responseType === 'json') {
                    throw new DOMException(
                        "responseText ist bei responseType 'json' nicht verfügbar",
                        'InvalidStateError');
                  }
                  return this._state < LOADING ? '' : this._responseText;
                }
                get response() {
                  if (this._responseType === 'json') {
                    if (this._state !== DONE) return null;
                    try { return JSON.parse(this._responseText); } catch (error) { return null; }
                  }
                  return this.responseText;
                }
                addEventListener(type, listener) {
                  type = String(type);
                  const listeners = this._listeners.get(type) || [];
                  if (typeof listener === 'function' && !listeners.includes(listener)) {
                    listeners.push(listener);
                  }
                  this._listeners.set(type, listeners);
                }
                removeEventListener(type, listener) {
                  const listeners = this._listeners.get(String(type)) || [];
                  this._listeners.set(String(type),
                      listeners.filter(candidate => candidate !== listener));
                }
                dispatchEvent(event) {
                  this._fire(String(event && event.type));
                  return true;
                }
                _fire(type, loaded) {
                  loaded = loaded === undefined ? 0 : loaded;
                  const event = {
                    type: type, target: this, currentTarget: this,
                    lengthComputable: loaded > 0, loaded: loaded, total: loaded
                  };
                  const handler = this['on' + type];
                  const listeners = [...(this._listeners.get(type) || [])];
                  if (typeof handler === 'function') listeners.unshift(handler);
                  for (const listener of listeners) {
                    try {
                      listener.call(this, event);
                    } catch (error) {
                      console.error('XMLHttpRequest-Ereignisbehandlung (' + type + '): '
                          + String(error && error.message || error));
                    }
                  }
                }
                open(method, url, async) {
                  method = String(method).toUpperCase();
                  if (method === 'CONNECT' || method === 'TRACE' || method === 'TRACK') {
                    throw new DOMException(
                        'XMLHttpRequest: Methode ' + method + ' ist nicht erlaubt',
                        'SecurityError');
                  }
                  this._generation++;
                  this._reset();
                  this._method = method;
                  this._url = String(url);
                  this._async = async === undefined ? true : Boolean(async);
                  this._state = OPENED;
                  this._fire('readystatechange');
                }
                setRequestHeader(name, value) {
                  if (this._state !== OPENED || this._sent) {
                    throw new DOMException(
                        'setRequestHeader: open() wurde noch nicht aufgerufen',
                        'InvalidStateError');
                  }
                  this._requestHeaders.push([String(name), String(value)]);
                }
                overrideMimeType(mimeType) {
                  if (this._state >= LOADING) {
                    throw new DOMException(
                        'overrideMimeType: Antwort wird bereits geladen', 'InvalidStateError');
                  }
                }
                getResponseHeader(name) {
                  if (this._state < HEADERS_RECEIVED) return null;
                  name = String(name).toLowerCase();
                  const values = [];
                  for (const pair of this._headers) {
                    if (pair[0] === name) values.push(pair[1]);
                  }
                  return values.length === 0 ? null : values.join(', ');
                }
                getAllResponseHeaders() {
                  if (this._state < HEADERS_RECEIVED) return '';
                  const names = [...new Set(this._headers.map(pair => pair[0]))].sort();
                  return names.map(name =>
                      name + ': ' + this.getResponseHeader(name) + '\\r\\n').join('');
                }
                abort() {
                  this._generation++;
                  const active = this._sent && this._state !== DONE;
                  this._sent = false;
                  this._status = 0;
                  this._statusText = '';
                  this._responseText = '';
                  if (active) {
                    this._state = DONE;
                    this._fire('readystatechange');
                    this._fire('abort');
                    this._fire('loadend');
                  }
                  this._state = UNSENT;
                }
                _applyResponse(finalUrl, status, statusText, headerPairs) {
                  this._responseUrl = String(finalUrl);
                  this._status = Number(status);
                  this._statusText = String(statusText);
                  this._headers = [];
                  for (let i = 0; i + 1 < headerPairs.length; i += 2) {
                    this._headers.push(
                        [String(headerPairs[i]).toLowerCase(), String(headerPairs[i + 1])]);
                  }
                }
                send(body) {
                  if (this._state !== OPENED || this._sent) {
                    throw new DOMException(
                        'send: XMLHttpRequest ist nicht geöffnet', 'InvalidStateError');
                  }
                  if (this._method !== 'GET') {
                    throw new DOMException(
                        'XMLHttpRequest: Methode ' + this._method + ' wird noch nicht unterstützt',
                        'NotSupportedError');
                  }
                  this._sent = true;
                  const generation = this._generation;
                  if (!this._async) {
                    const result = __browicyFetchSync(this._url);
                    if (!result[0]) {
                      this._state = DONE;
                      this._sent = false;
                      throw new DOMException(String(result[1]), 'NetworkError');
                    }
                    this._applyResponse(result[1], result[2], result[3], result[4]);
                    this._responseText = String(result[5]);
                    this._state = DONE;
                    this._fire('readystatechange');
                    this._fire('load', this._responseText.length);
                    this._fire('loadend', this._responseText.length);
                    return;
                  }
                  this._fire('loadstart');
                  let timerId = 0;
                  let timedOut = false;
                  const timeoutMillis = Number(this.timeout);
                  if (Number.isFinite(timeoutMillis) && timeoutMillis > 0) {
                    timerId = setTimeout(() => {
                      if (generation !== this._generation || this._state === DONE) return;
                      timedOut = true;
                      this._status = 0;
                      this._statusText = '';
                      this._state = DONE;
                      this._fire('readystatechange');
                      this._fire('timeout');
                      this._fire('loadend');
                    }, timeoutMillis);
                  }
                  const stillCurrent = () => generation === this._generation && !timedOut;
                  __browicyFetch(this._url,
                      (finalUrl, status, statusText, headerPairs, bodyText) => {
                        if (!stillCurrent()) return;
                        if (timerId) clearTimeout(timerId);
                        this._applyResponse(finalUrl, status, statusText, headerPairs);
                        this._state = HEADERS_RECEIVED;
                        this._fire('readystatechange');
                        this._state = LOADING;
                        this._responseText = String(bodyText);
                        this._fire('readystatechange');
                        this._fire('progress', this._responseText.length);
                        this._state = DONE;
                        this._fire('readystatechange');
                        this._fire('load', this._responseText.length);
                        this._fire('loadend', this._responseText.length);
                      },
                      message => {
                        if (!stillCurrent()) return;
                        if (timerId) clearTimeout(timerId);
                        this._status = 0;
                        this._statusText = '';
                        this._state = DONE;
                        this._fire('readystatechange');
                        this._fire('error');
                        this._fire('loadend');
                      });
                }
              }
              const STATES = { UNSENT: UNSENT, OPENED: OPENED, HEADERS_RECEIVED: HEADERS_RECEIVED,
                  LOADING: LOADING, DONE: DONE };
              for (const name of Object.keys(STATES)) {
                XMLHttpRequest[name] = STATES[name];
                XMLHttpRequest.prototype[name] = STATES[name];
              }
              globalThis.XMLHttpRequest = XMLHttpRequest;
            })();
            """;

    static final String EVENT_LISTENER_INVOKER = """
            (listener, currentTarget, event) => {
              if (typeof listener === 'function') {
                return listener.call(currentTarget, event);
              }
              return listener.handleEvent.call(listener, event);
            }
            """;

    static final String DOM_OPERATION_WRAPPER = """
            operation => (...args) => {
              try { return operation(...args); }
              catch (error) {
                const message = String(error && error.message || error);
                const marker = 'DOM_EXCEPTION|';
                const start = message.indexOf(marker);
                if (start < 0) throw error;
                const fields = message.substring(start + marker.length).split('|');
                throw new DOMException(fields.slice(2).join('|'), fields[0]);
              }
            }
            """;

    private final long statementLimit;

    public JavaScriptEngine() {
        this(DEFAULT_STATEMENT_LIMIT);
    }

    public JavaScriptEngine(long statementLimit) {
        if (statementLimit <= 0) {
            throw new IllegalArgumentException("Statement-Limit muss positiv sein");
        }
        this.statementLimit = statementLimit;
    }

    public PageRuntime createPageRuntime(Document document) {
        return createPageRuntime(document, PageRuntimeObserver.NO_OP);
    }

    public PageRuntime createPageRuntime(Document document, PageRuntimeObserver observer) {
        return createPageRuntime(document, observer, null);
    }

    public PageRuntime createPageRuntime(Document document,
                                         PageRuntimeObserver observer,
                                         JsFetchBackend fetchBackend) {
        return new GraalPageRuntime(document, statementLimit, observer, fetchBackend);
    }

    public PageRuntime createPageRuntime(Document document,
                                         PageRuntimeObserver observer,
                                         JsFetchBackend fetchBackend,
                                         JsCookieStore cookieStore) {
        return new GraalPageRuntime(document, statementLimit, observer, fetchBackend, cookieStore);
    }

    public JsExecutionResult runScripts(Document document) {
        Objects.requireNonNull(document, "document");
        List<JavaScriptSource> scripts = new ArrayList<>();
        for (Element script : document.getElementsByTagName("script")) {
            if (script.hasAttribute("src")) {
                continue;
            }
            String code = script.getTextContent();
            if (!code.isBlank()) {
                scripts.add(new JavaScriptSource(
                        code, script, "inline-script-" + (scripts.size() + 1) + ".js"));
            }
        }
        return executeSequence(document, scripts);
    }

    public JsExecutionResult runScripts(Document document, List<JavaScriptSource> scripts) {
        Objects.requireNonNull(scripts, "scripts");
        return executeSequence(document, List.copyOf(scripts));
    }

    public JsExecutionResult runScriptSequence(
            Document document, Iterable<JavaScriptSource> scripts) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(scripts, "scripts");
        return executeSequence(document, scripts);
    }

    public JsExecutionResult execute(Document document, String script) {
        return executeSequence(document, List.of(new JavaScriptSource(script, null, "script.js")));
    }

    private JsExecutionResult executeSequence(
            Document document, Iterable<JavaScriptSource> scripts) {
        Objects.requireNonNull(document, "document");
        try (PageRuntime runtime = createPageRuntime(document)) {
            for (JavaScriptSource source : scripts) {
                runtime.execute(source);
            }
            completeLifecycle(document, runtime);
            runtime.awaitIdle();
            return ((GraalPageRuntime) runtime).snapshotResult();
        }
    }

    private static void completeLifecycle(Document document, PageRuntime runtime) {
        runtime.enqueueTask(() -> document.transitionTo(DocumentReadyState.INTERACTIVE));
        runtime.submitEvent(document, new Event("DOMContentLoaded", true, false)).join();
        runtime.enqueueTask(() -> document.transitionTo(DocumentReadyState.COMPLETE));
        Element body = document.getBody();
        if (body != null) {
            runtime.submitEvent(body, new Event("load", false, false)).join();
        }
    }
}
