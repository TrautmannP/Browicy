package com.browicy.engine.js;

import com.browicy.engine.dom.Document;
import com.browicy.engine.dom.DocumentReadyState;
import com.browicy.engine.dom.Element;
import com.browicy.engine.dom.Event;
import com.browicy.engine.css.StyleSheetRegistry;
import com.browicy.engine.html.DocumentResourceScanner;
import com.browicy.engine.html.StyleSheetResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JavaScriptEngine {

    public static final long DEFAULT_STATEMENT_LIMIT = 10_000_000;

    static final String BROWSER_BOOTSTRAP = """
            globalThis.window = globalThis;
            globalThis.self = globalThis;
            globalThis.top = globalThis;
            globalThis.parent = globalThis;
            globalThis.frames = new Proxy(Object.create(null), {
              get: (_, key) => {
                const frames = Array.from(document.getElementsByTagName('iframe'));
                if (key === 'length') return frames.length;
                if (key === Symbol.iterator) return function* () {
                  for (const frame of frames) yield frame.contentWindow;
                };
                const index = String(key).match(/^(0|[1-9]\\d*)$/) ? Number(key) : -1;
                if (index >= 0) return index < frames.length ? frames[index].contentWindow : undefined;
                const named = frames.find(frame => frame.name === String(key) || frame.id === String(key));
                return named == null ? undefined : named.contentWindow;
              }
            });
            globalThis.onload = null;
            const __browicyTimeOrigin = Date.now();
            const __browicyNavigationEntry = Object.freeze({
              entryType: 'navigation', name: String(document.URL || ''), startTime: 0,
              duration: 0, responseStart: 0, responseEnd: 0, domInteractive: 0,
              domContentLoadedEventStart: 0, domContentLoadedEventEnd: 0,
              loadEventStart: 0, loadEventEnd: 0, type: 'navigate', redirectCount: 0
            });
            const __browicyPerformanceEntries = [];
            const __browicyPerformanceEntry = (name, entryType, startTime, duration) =>
                Object.freeze({ name: String(name), entryType: entryType,
                                startTime: startTime, duration: duration || 0 });
            const __browicyPerformance = {
              timeOrigin: __browicyTimeOrigin,
              timing: Object.freeze({
                navigationStart: __browicyTimeOrigin,
                responseStart: __browicyTimeOrigin
              }),
              now: () => Math.max(0, Date.now() - __browicyTimeOrigin),
              mark: name => {
                __browicyPerformanceEntries.push(
                    __browicyPerformanceEntry(name, 'mark', __browicyPerformance.now(), 0));
              },
              measure: (name, startMark, endMark) => {
                const start = startMark == null ? 0
                    : (__browicyPerformance.getEntriesByName(startMark)[0] || {}).startTime || 0;
                const end = endMark == null ? __browicyPerformance.now()
                    : (__browicyPerformance.getEntriesByName(endMark)[0] || {}).startTime
                        || __browicyPerformance.now();
                __browicyPerformanceEntries.push(__browicyPerformanceEntry(
                    name, 'measure', start, Math.max(0, end - start)));
              },
              getEntriesByType: type => String(type) === 'navigation'
                  ? [__browicyNavigationEntry]
                  : __browicyPerformanceEntries.filter(
                      entry => entry.entryType === String(type)),
              getEntriesByName: name => {
                const matches = __browicyPerformanceEntries.filter(
                    entry => entry.name === String(name));
                if (matches.length > 0) return matches;
                return String(name) === __browicyNavigationEntry.name
                    ? [__browicyNavigationEntry] : [];
              },
              getEntries: () => [__browicyNavigationEntry, ...__browicyPerformanceEntries],
              clearMarks: () => { __browicyPerformanceEntries.length = 0; },
              clearMeasures: () => { __browicyPerformanceEntries.length = 0; }
            };
            globalThis.performance = Object.freeze(__browicyPerformance);
            globalThis.navigator = Object.freeze({
              userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
                + 'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 '
                + 'Safari/537.36 Browicy/0.1',
              appVersion: '5.0 (Windows NT 10.0; Win64; x64)',
              vendor: 'Google Inc.', language: 'de-DE', languages: ['de-DE', 'de'],
              platform: 'Win32', cookieEnabled: true, onLine: true,
              hardwareConcurrency: 8, maxTouchPoints: 0,
              sendBeacon: () => false
            });
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
            globalThis.Element = function Element() { throw new TypeError('Illegal constructor'); };
            Element.prototype = Object.create(Node.prototype);
            Object.defineProperty(Element, Symbol.hasInstance, {
              value: candidate => candidate != null && (
                Element.prototype.isPrototypeOf(candidate)
                || candidate.nodeType === Node.ELEMENT_NODE)
            });
            globalThis.CharacterData = function CharacterData() {
              throw new TypeError('Illegal constructor');
            };
            CharacterData.prototype = Object.create(Node.prototype);
            Object.defineProperty(CharacterData, Symbol.hasInstance, {
              value: candidate => candidate != null && (
                CharacterData.prototype.isPrototypeOf(candidate)
                || candidate.nodeType === Node.TEXT_NODE || candidate.nodeType === Node.COMMENT_NODE)
            });
            globalThis.DocumentType = function DocumentType() {
              throw new TypeError('Illegal constructor');
            };
            DocumentType.prototype = Object.create(Node.prototype);
            Object.defineProperty(DocumentType, Symbol.hasInstance, {
              value: candidate => candidate != null && (
                DocumentType.prototype.isPrototypeOf(candidate)
                || candidate.nodeType === Node.DOCUMENT_TYPE_NODE)
            });
            globalThis.Document = function Document() {
              return document.implementation.createHTMLDocument('');
            };
            Document.prototype = Object.create(Node.prototype);
            Object.defineProperty(Document, Symbol.hasInstance, {
              value: candidate => candidate != null && (
                Document.prototype.isPrototypeOf(candidate)
                || candidate.nodeType === Node.DOCUMENT_NODE)
            });
            globalThis.DocumentFragment = function DocumentFragment() {
              return document.createDocumentFragment();
            };
            DocumentFragment.prototype = Object.create(Node.prototype);
            Object.defineProperty(DocumentFragment, Symbol.hasInstance, {
              value: candidate => candidate != null && (
                DocumentFragment.prototype.isPrototypeOf(candidate)
                || candidate.nodeType === Node.DOCUMENT_FRAGMENT_NODE)
            });
            globalThis.HTMLElement = function HTMLElement() { throw new TypeError('Illegal constructor'); };
            HTMLElement.prototype = Object.create(Element.prototype);
            Object.defineProperty(HTMLElement, Symbol.hasInstance, {
              value: candidate => candidate != null && candidate.nodeType === 1
                && (candidate.namespaceURI == null
                    || candidate.namespaceURI === 'http://www.w3.org/1999/xhtml')
            });
            const __browicyElementInterfaces = {
              HTMLHtmlElement: ['html'], HTMLHeadElement: ['head'], HTMLBodyElement: ['body'],
              HTMLTitleElement: ['title'], HTMLMetaElement: ['meta'], HTMLLinkElement: ['link'],
              HTMLStyleElement: ['style'], HTMLScriptElement: ['script'], HTMLTemplateElement: ['template'],
              HTMLSlotElement: ['slot'], HTMLDivElement: ['div'], HTMLSpanElement: ['span'],
              HTMLParagraphElement: ['p'], HTMLHeadingElement: ['h1', 'h2', 'h3', 'h4', 'h5', 'h6'],
              HTMLUListElement: ['ul'], HTMLOListElement: ['ol'], HTMLLIElement: ['li'],
              HTMLAnchorElement: ['a'], HTMLImageElement: ['img'], HTMLCanvasElement: ['canvas'],
              HTMLAudioElement: ['audio'], HTMLVideoElement: ['video'], HTMLSourceElement: ['source'],
              HTMLIFrameElement: ['iframe'], HTMLFormElement: ['form'],
              HTMLInputElement: ['input'], HTMLButtonElement: ['button'], HTMLSelectElement: ['select'],
              HTMLOptionElement: ['option'], HTMLTextAreaElement: ['textarea'],
              HTMLBRElement: ['br'], HTMLHRElement: ['hr'], HTMLPreElement: ['pre'],
              HTMLQuoteElement: ['blockquote', 'q'], HTMLTableElement: ['table'],
              HTMLTableSectionElement: ['thead', 'tbody', 'tfoot'],
              HTMLTableRowElement: ['tr'], HTMLTableCellElement: ['td', 'th'],
              HTMLDataListElement: ['datalist'], HTMLLabelElement: ['label'],
              HTMLUnknownElement: []
            };
            for (const interfaceName of Object.keys(__browicyElementInterfaces)) {
              const tags = __browicyElementInterfaces[interfaceName];
              globalThis[interfaceName] = function () {
                throw new TypeError('Illegal constructor');
              };
              globalThis[interfaceName].prototype = Object.create(HTMLElement.prototype);
              Object.defineProperty(globalThis[interfaceName], Symbol.hasInstance, {
                value: candidate => candidate != null && typeof candidate === 'object'
                    && candidate.nodeType === 1
                    && (tags.length === 0 || tags.includes(String(candidate.tagName).toLowerCase()))
              });
            }
            globalThis.SVGElement = function SVGElement() { throw new TypeError('Illegal constructor'); };
            SVGElement.prototype = Object.create(Element.prototype);
            Object.defineProperty(SVGElement, Symbol.hasInstance, {
              value: candidate => candidate != null && candidate.nodeType === 1
                && candidate.namespaceURI === 'http://www.w3.org/2000/svg'
            });
            globalThis.MathMLElement = function MathMLElement() {
              throw new TypeError('Illegal constructor');
            };
            MathMLElement.prototype = Object.create(Element.prototype);
            Object.defineProperty(MathMLElement, Symbol.hasInstance, {
              value: candidate => candidate != null && candidate.nodeType === 1
                && candidate.namespaceURI === 'http://www.w3.org/1998/Math/MathML'
            });
            // Host-Elemente (ProxyObject) unterstützen Object.defineProperty nicht
            // nativ. Frameworks wie Vue legen darüber Expando-Eigenschaften an
            // (z.B. el.__vnode, el.__vueParentComponent, __vue__ auf Text-Knoten).
            // Wertdeskriptoren werden deshalb als normale Member-Zuweisung auf das
            // Knoten-Objekt durchgereicht; alles andere geht an die native Implementierung.
            const __browicyNativeDefineProperty = Object.defineProperty;
            Object.defineProperty = function (target, key, descriptor) {
              if (target != null
                      && (typeof target === 'object' || typeof target === 'function')
                      && typeof target.nodeType === 'number'
                      && descriptor != null
                      && Object.prototype.hasOwnProperty.call(descriptor, 'value')) {
                target[key] = descriptor.value;
                return target;
              }
              return __browicyNativeDefineProperty(target, key, descriptor);
            };
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
            globalThis.dispatchEvent = event => {
              const type = String(event && event.type);
              for (const listener of [...(__windowListeners.get(type) || [])]) listener.call(window, event);
              return !(event && event.defaultPrevented);
            };
            globalThis.CSS = Object.freeze({
              supports: (...args) => __browicyCssSupports(...args),
              escape: value => {
                const str = String(value);
                const length = str.length;
                const first = str.charCodeAt(0);
                let result = '';
                for (let index = 0; index < length; index++) {
                  const codeUnit = str.charCodeAt(index);
                  if (codeUnit === 0x0000) {
                    result += '\uFFFD';
                  } else if (codeUnit >= 0x0001 && codeUnit <= 0x001F
                          || codeUnit >= 0x007F
                          || (index === 0 && codeUnit >= 0x0030 && codeUnit <= 0x0039)
                          || (index === 1 && codeUnit >= 0x0030 && codeUnit <= 0x0039
                              && first === 0x002D)) {
                    result += '\\\\' + codeUnit.toString(16) + ' ';
                  } else if (index === 0 && length === 1 && codeUnit === 0x002D) {
                    result += '\\\\' + str.charAt(index);
                  } else if (codeUnit >= 0x0080 || codeUnit === 0x002D
                          || codeUnit === 0x005F
                          || codeUnit >= 0x0030 && codeUnit <= 0x0039
                          || codeUnit >= 0x0041 && codeUnit <= 0x005A
                          || codeUnit >= 0x0061 && codeUnit <= 0x007A) {
                    result += str.charAt(index);
                  } else {
                    result += '\\\\' + str.charAt(index);
                  }
                }
                return result;
              }
            });
            globalThis.getComputedStyle = element => __browicyGetComputedStyle(element);
            const __browicyLocationUrl = String(document.URL || 'about:blank');
            const __browicyLocationParts = __browicyLocationParse(__browicyLocationUrl);
            const __browicyResolveAgainst = (baseUrl, target) => {
              target = String(target == null ? '' : target);
              if (/^[a-z][a-z0-9+.-]*:/i.test(target)) return target;
              const parts = __browicyLocationParse(String(baseUrl == null ? '' : baseUrl));
              if (target.startsWith('//')) return parts.protocol + target;
              const queryIndex = target.search(/[?#]/);
              const suffix = queryIndex >= 0 ? target.substring(queryIndex) : '';
              const pathTarget = queryIndex >= 0 ? target.substring(0, queryIndex) : target;
              if (pathTarget.startsWith('/')) {
                return parts.origin + pathTarget + suffix;
              }
              const basePath = parts.pathname;
              const baseDir = basePath.lastIndexOf('/') >= 0
                  ? basePath.substring(0, basePath.lastIndexOf('/') + 1) : '/';
              const segments = [];
              for (const part of (baseDir + pathTarget).split('/')) {
                if (part === '' || part === '.') continue;
                if (part === '..') { if (segments.length > 0) segments.pop(); }
                else segments.push(part);
              }
              return parts.origin + '/' + segments.join('/') + suffix;
            };
            const __browicyResolveUrl = target =>
                __browicyResolveAgainst(__browicyLocationUrl, target);
            globalThis.URL = class URL {
              constructor(url, base) {
                const raw = String(url == null ? '' : url);
                const resolved = base == null
                    ? raw : __browicyResolveAgainst(String(base), raw);
                if (!/^[a-z][a-z0-9+.-]*:/i.test(resolved)) {
                  throw new TypeError('Ungültige URL: ' + raw);
                }
                this._parts = __browicyLocationParse(resolved);
                this._href = resolved;
                this._searchParams = null;
              }
              get href() { return this._href; }
              get protocol() { return this._parts.protocol; }
              get host() { return this._parts.host; }
              get hostname() { return this._parts.hostname; }
              get port() { return this._parts.port; }
              get pathname() { return this._parts.pathname; }
              get search() { return this._parts.search; }
              get hash() { return this._parts.hash; }
              get origin() { return this._parts.origin; }
              get searchParams() {
                if (this._searchParams == null) {
                  this._searchParams = new URLSearchParams(this.search);
                }
                return this._searchParams;
              }
              toString() { return this._href; }
              toJSON() { return this._href; }
            };
            globalThis.Blob = class Blob {
              constructor(parts, options) {
                this._text = '';
                for (const part of parts || []) {
                  this._text += String(part);
                }
                this.size = this._text.length;
                this.type = (options && options.type) || '';
              }
              text() { return Promise.resolve(this._text); }
              arrayBuffer() {
                return Promise.resolve(new Uint8Array(this._text.length));
              }
              slice(start, end, contentType) {
                const from = Math.max(0, Number(start) || 0);
                const to = end == null ? this.size : Math.max(from, Number(end) || 0);
                return new Blob([this._text.substring(from, to)],
                    { type: contentType || this.type });
              }
              toString() { return '[object Blob]'; }
            };
            let __browicyLocationHash = __browicyLocationParts.hash;
            const __browicyLocation = {
              get href() {
                if (__browicyLocationParts.host === '') return __browicyLocationUrl;
                return __browicyLocationParts.protocol + '//' + __browicyLocationParts.host
                    + __browicyLocationParts.pathname + __browicyLocationParts.search
                    + __browicyLocationHash;
              },
              set href(value) { __browicyNavigate(__browicyResolveUrl(value), true); },
              get protocol() { return __browicyLocationParts.protocol; },
              get host() { return __browicyLocationParts.host; },
              get hostname() { return __browicyLocationParts.hostname; },
              get port() { return __browicyLocationParts.port; },
              get pathname() { return __browicyLocationParts.pathname; },
              get search() { return __browicyLocationParts.search; },
              get hash() { return __browicyLocationHash; },
              set hash(value) {
                value = String(value);
                if (value !== '' && !value.startsWith('#')) value = '#' + value;
                __browicyLocationHash = value;
              },
              get origin() { return __browicyLocationParts.origin; },
              assign: target => __browicyNavigate(__browicyResolveUrl(target), false),
              replace: target => __browicyNavigate(__browicyResolveUrl(target), true),
              reload: () => __browicyNavigate(__browicyLocation.href, true),
              toString: () => __browicyLocation.href
            };
            Object.defineProperty(globalThis, 'location', {
              get: () => __browicyLocation,
              set: value => {
                if (value != null && typeof value === 'object') return;
                __browicyNavigate(__browicyResolveUrl(value), true);
              },
              configurable: false
            });
            globalThis.open = (url, target) => {
              const targetName = target == null ? '' : String(target);
              if (targetName === '_blank') return null;
              if (url != null && String(url) !== '') {
                __browicyNavigate(__browicyResolveUrl(url), false);
              }
              return window;
            };
            globalThis.window.open = globalThis.open;
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
            globalThis.IntersectionObserver = class IntersectionObserver {
              constructor(callback, options) {
                if (typeof callback !== 'function') {
                  throw new TypeError('IntersectionObserver: callback must be a function');
                }
                this._callback = callback;
                this._targets = new Set();
                this.root = options == null ? null : options.root;
                this.rootMargin = options == null ? '0px' : String(options.rootMargin || '0px');
                const thresholds = options == null ? [0] : options.threshold;
                this.thresholds = Array.isArray(thresholds) ? thresholds.slice() : [thresholds];
              }
              observe(target) { this._targets.add(target); }
              unobserve(target) { this._targets.delete(target); }
              disconnect() { this._targets.clear(); }
              takeRecords() { return []; }
            };
            globalThis.ResizeObserver = class ResizeObserver {
              constructor(callback) {
                if (typeof callback !== 'function') {
                  throw new TypeError('ResizeObserver: callback must be a function');
                }
                this._callback = callback;
                this._targets = new Set();
              }
              observe(target) { this._targets.add(target); }
              unobserve(target) { this._targets.delete(target); }
              disconnect() { this._targets.clear(); }
            };
            globalThis.screen = Object.freeze({
              width: 1920, height: 1080, availWidth: 1920, availHeight: 1040,
              colorDepth: 24, pixelDepth: 24,
              orientation: Object.freeze({ type: 'landscape-primary', angle: 0 })
            });
            globalThis.escape = value => {
              const text = String(value == null ? 'undefined' : value);
              let result = '';
              for (let index = 0; index < text.length; index++) {
                const character = text.charAt(index);
                const code = text.charCodeAt(index);
                if (code >= 48 && code <= 57 || code >= 65 && code <= 90
                        || code >= 97 && code <= 122 || character === '*'
                        || character === '@' || character === '_' || character === '+'
                        || character === '-' || character === '.' || character === '/') {
                  result += character;
                } else if (code < 256) {
                  result += '%' + code.toString(16).toUpperCase().padStart(2, '0');
                } else {
                  result += '%u' + code.toString(16).toUpperCase().padStart(4, '0');
                }
              }
              return result;
            };
            globalThis.unescape = value => {
              const text = String(value == null ? 'undefined' : value);
              return text
                  .replace(/%u([0-9A-Fa-f]{4})/g, (_, hex) =>
                      String.fromCharCode(parseInt(hex, 16)))
                  .replace(/%([0-9A-Fa-f]{2})/g, (_, hex) =>
                      String.fromCharCode(parseInt(hex, 16)));
            };
            globalThis.AbortSignal = class AbortSignal {
              constructor() {
                this.aborted = false;
                this.reason = undefined;
                this._listeners = new Map();
              }
              addEventListener(type, callback) {
                type = String(type);
                const listeners = this._listeners.get(type) || [];
                if (typeof callback === 'function' && !listeners.includes(callback)) listeners.push(callback);
                this._listeners.set(type, listeners);
              }
              removeEventListener(type, callback) {
                const listeners = this._listeners.get(String(type)) || [];
                this._listeners.set(String(type), listeners.filter(item => item !== callback));
              }
              dispatchEvent(event) {
                for (const listener of [...(this._listeners.get(String(event && event.type)) || [])]) {
                  listener.call(this, event);
                }
                return true;
              }
              static abort(reason) {
                const signal = new AbortSignal();
                signal.aborted = true;
                signal.reason = reason;
                return signal;
              }
              static timeout(millis) {
                const signal = new AbortSignal();
                setTimeout(() => signal.abort(new DOMException('Timeout abgelaufen', 'TimeoutError')),
                    Number(millis) || 0);
                return signal;
              }
            };
            globalThis.AbortController = class AbortController {
              constructor() {
                this.signal = new AbortSignal();
              }
              abort(reason) {
                if (this.signal.aborted) return;
                this.signal.aborted = true;
                this.signal.reason = reason;
                this.signal.dispatchEvent(new Event('abort'));
              }
            };
            globalThis.requestAnimationFrame = callback => {
              const id = setTimeout(() => {
                if (typeof callback === 'function') callback(__browicyPerformance.now());
              }, 16);
              return id;
            };
            globalThis.cancelAnimationFrame = id => clearTimeout(id);
            const __browicyBase64Chars =
                'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
            globalThis.btoa = value => {
              const text = String(value);
              let result = '';
              for (let index = 0; index < text.length; index += 3) {
                const b1 = text.charCodeAt(index);
                const b2 = index + 1 < text.length ? text.charCodeAt(index + 1) : NaN;
                const b3 = index + 2 < text.length ? text.charCodeAt(index + 2) : NaN;
                result += __browicyBase64Chars.charAt(b1 >> 2);
                result += __browicyBase64Chars.charAt(
                    ((b1 & 3) << 4) | (isNaN(b2) ? 0 : b2 >> 4));
                result += isNaN(b2) ? '='
                    : __browicyBase64Chars.charAt(((b2 & 15) << 2) | (isNaN(b3) ? 0 : b3 >> 6));
                result += isNaN(b3) ? '=' : __browicyBase64Chars.charAt(b3 & 63);
              }
              return result;
            };
            globalThis.atob = value => {
              const text = String(value).replace(/=+$/, '');
              let result = '';
              for (let index = 0; index < text.length; index += 4) {
                const c1 = __browicyBase64Chars.indexOf(text.charAt(index));
                const c2 = __browicyBase64Chars.indexOf(text.charAt(index + 1));
                const c3 = __browicyBase64Chars.indexOf(text.charAt(index + 2));
                const c4 = __browicyBase64Chars.indexOf(text.charAt(index + 3));
                result += String.fromCharCode((c1 << 2) | (c2 >> 4));
                if (index + 2 < text.length) {
                  result += String.fromCharCode(((c2 & 15) << 4) | (c3 >> 2));
                }
                if (index + 3 < text.length) {
                  result += String.fromCharCode(((c3 & 3) << 6) | c4);
                }
              }
              return result;
            };
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
            globalThis.CustomEvent = function CustomEvent(type, init) {
              init = init || {};
              const event = document.createEvent('CustomEvent');
              event.initCustomEvent(String(type), Boolean(init.bubbles), Boolean(init.cancelable),
                                    init.detail === undefined ? null : init.detail);
              return event;
            };
            CustomEvent.prototype = Object.create(Event.prototype);
            Object.defineProperty(CustomEvent, Symbol.hasInstance, {
              value: candidate => candidate != null && typeof candidate.initCustomEvent === 'function'
            });
            globalThis.PromiseRejectionEvent = class PromiseRejectionEvent extends Event {
              constructor(type, init) {
                super(String(type), init);
                this.promise = init == null ? null : init.promise;
                this.reason = init == null ? null : init.reason;
              }
            };
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
              const METHOD_PATTERN = /^[A-Za-z!#$%&'*+.^_`|~0-9-]+$/;
              const FORBIDDEN_METHODS = new Set(['CONNECT', 'TRACE', 'TRACK']);
              const FORBIDDEN_REQUEST_HEADERS = new Set([
                'accept-charset', 'accept-encoding', 'access-control-request-headers',
                'access-control-request-method', 'connection', 'content-length', 'cookie',
                'cookie2', 'date', 'dnt', 'expect', 'host', 'keep-alive', 'origin',
                'referer', 'te', 'trailer', 'transfer-encoding', 'upgrade',
                'user-agent', 'via'
              ]);
              const normalizeHeaderName = name => {
                name = String(name).trim().toLowerCase();
                if (name.length === 0 || !METHOD_PATTERN.test(name)) {
                  throw new TypeError('Ungültiger HTTP-Headername: ' + name);
                }
                return name;
              };
              const normalizeHeaderValue = value => {
                value = String(value).trim();
                for (let index = 0; index < value.length; index++) {
                  const code = value.charCodeAt(index);
                  if ((code < 0x20 && code !== 0x09) || code === 0x7f) {
                    throw new TypeError('Ungültiger HTTP-Headerwert');
                  }
                }
                return value;
              };
              const normalizeMethod = method => {
                method = String(method == null ? 'GET' : method).trim().toUpperCase();
                if (method.length === 0 || !METHOD_PATTERN.test(method)) {
                  throw new TypeError('Ungültige HTTP-Methode: ' + method);
                }
                return method;
              };
              const isForbiddenRequestHeader = name => {
                name = String(name).toLowerCase();
                return FORBIDDEN_REQUEST_HEADERS.has(name)
                    || name.startsWith('proxy-') || name.startsWith('sec-');
              };
              const isForbiddenMethod = method => FORBIDDEN_METHODS.has(method);

              class Headers {
                constructor(init) {
                  this._entries = [];
                  if (init == null) return;
                  if (init instanceof Headers) {
                    for (const entry of init._entries) this.append(entry[0], entry[1]);
                  } else if (Array.isArray(init)) {
                    for (const pair of init) {
                      if (pair == null || pair.length !== 2) {
                        throw new TypeError('Ungültiges Header-Paar');
                      }
                      this.append(pair[0], pair[1]);
                    }
                  } else if (typeof init === 'object') {
                    for (const name of Object.keys(init)) this.append(name, init[name]);
                  } else {
                    throw new TypeError('Ungültige Headers-Initialisierung');
                  }
                }
                append(name, value) {
                  this._entries.push([
                    normalizeHeaderName(name), normalizeHeaderValue(value)
                  ]);
                }
                set(name, value) { this.delete(name); this.append(name, value); }
                delete(name) {
                  name = normalizeHeaderName(name);
                  this._entries = this._entries.filter(entry => entry[0] !== name);
                }
                get(name) {
                  name = normalizeHeaderName(name);
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

              const prepareBody = (body, headers, method, rejectGetBody) => {
                if (body == null) return null;
                if (method === 'GET' || method === 'HEAD') {
                  if (rejectGetBody) {
                    throw new TypeError('Request-Body ist für ' + method + ' nicht erlaubt');
                  }
                  return null;
                }
                if (typeof body !== 'string') {
                  if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) {
                    if (!headers.has('content-type')) {
                      headers.set('Content-Type',
                          'application/x-www-form-urlencoded;charset=UTF-8');
                    }
                    return String(body);
                  }
                  throw new TypeError(
                      'Dieser Request-Body-Typ wird noch nicht unterstützt');
                }
                if (!headers.has('content-type')) {
                  headers.set('Content-Type', 'text/plain;charset=UTF-8');
                }
                return body;
              };
              const requestHeaderPairs = headers => {
                const pairs = [];
                for (const [name, value] of headers) {
                  if (!isForbiddenRequestHeader(name)) pairs.push(name, value);
                }
                return pairs;
              };
              const requestSupport = Object.freeze({
                normalizeHeaderName, normalizeHeaderValue, normalizeMethod,
                isForbiddenMethod, isForbiddenRequestHeader, prepareBody,
                requestHeaderPairs
              });
              Object.defineProperty(globalThis, '__browicyRequestSupport', {
                value: requestSupport, enumerable: false, writable: false,
                configurable: false
              });

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
                  try {
                    const inputObject = input !== null && typeof input === 'object' ? input : null;
                    const options = init == null ? null : Object(init);
                    const url = String(inputObject && inputObject.url !== undefined
                        ? inputObject.url : input);
                    const method = normalizeMethod(options && options.method != null
                        ? options.method
                        : inputObject && inputObject.method != null ? inputObject.method : 'GET');
                    if (isForbiddenMethod(method)) {
                      throw new TypeError('fetch: Methode ' + method + ' ist nicht erlaubt');
                    }
                    const headerInit = options && options.headers !== undefined
                        ? options.headers
                        : inputObject && inputObject.headers !== undefined
                            ? inputObject.headers : undefined;
                    const headers = new Headers(headerInit);
                    let body = inputObject && inputObject.body !== undefined
                        ? inputObject.body : null;
                    if (options && Object.prototype.hasOwnProperty.call(options, 'body')) {
                      body = options.body;
                    }
                    const bodyText = prepareBody(body, headers, method, true);
                    __browicyFetch(url, method, requestHeaderPairs(headers), bodyText,
                        (finalUrl, status, statusText, headerPairs, responseBodyText) => {
                          try {
                            const responseHeaders = new Headers();
                            for (let i = 0; i + 1 < headerPairs.length; i += 2) {
                              responseHeaders.append(headerPairs[i], headerPairs[i + 1]);
                            }
                            const response = new Response(responseBodyText,
                                { status: status, statusText: statusText,
                                  headers: responseHeaders });
                            response.url = String(finalUrl);
                            resolve(response);
                          } catch (error) {
                            reject(error);
                          }
                        },
                        message => reject(new TypeError(String(message))));
                  } catch (error) {
                    reject(error instanceof TypeError
                        ? error : new TypeError('fetch: ' + String(error && error.message || error)));
                  }
                });
              };
            })();
            """;

    static final String XHR_BOOTSTRAP = """
            (() => {
              'use strict';
              const requestSupport = globalThis.__browicyRequestSupport;
              const UNSENT = 0, OPENED = 1, HEADERS_RECEIVED = 2, LOADING = 3, DONE = 4;
              class XMLHttpRequest {
                constructor() {
                  this._listeners = new Map();
                  this._generation = 0;
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
                  this._requestHeaders = [];
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
                    if (this._state !== DONE || this._responseText === '') return null;
                    try { return JSON.parse(this._responseText); }
                    catch (error) { return null; }
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
                  method = requestSupport.normalizeMethod(method);
                  if (requestSupport.isForbiddenMethod(method)) {
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
                  name = requestSupport.normalizeHeaderName(name);
                  value = requestSupport.normalizeHeaderValue(value);
                  if (requestSupport.isForbiddenRequestHeader(name)) return;
                  const existing = this._requestHeaders.find(pair => pair[0] === name);
                  if (existing) existing[1] += ', ' + value;
                  else this._requestHeaders.push([name, value]);
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
                _prepareRequest(body) {
                  const headers = new Headers(this._requestHeaders);
                  let bodyText;
                  try {
                    bodyText = requestSupport.prepareBody(
                        body, headers, this._method, false);
                  } catch (error) {
                    throw new DOMException(
                        String(error && error.message || error), 'NotSupportedError');
                  }
                  return [requestSupport.requestHeaderPairs(headers), bodyText];
                }
                send(body) {
                  if (this._state !== OPENED || this._sent) {
                    throw new DOMException(
                        'send: XMLHttpRequest ist nicht geöffnet', 'InvalidStateError');
                  }
                  const request = this._prepareRequest(body);
                  const requestHeaders = request[0];
                  const requestBody = request[1];
                  this._sent = true;
                  const generation = this._generation;
                  if (!this._async) {
                    const result = __browicyFetchSync(
                        this._url, this._method, requestHeaders, requestBody);
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
                  __browicyFetch(this._url, this._method, requestHeaders, requestBody,
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
        return createPageRuntime(document, observer, fetchBackend, null);
    }

    public PageRuntime createPageRuntime(Document document,
                                         PageRuntimeObserver observer,
                                         JsFetchBackend fetchBackend,
                                         JsCookieStore cookieStore) {
        return createPageRuntime(document, observer, fetchBackend, cookieStore,
                defaultStyleSheets(document), () -> { });
    }

    public PageRuntime createPageRuntime(Document document,
                                         PageRuntimeObserver observer,
                                         JsFetchBackend fetchBackend,
                                         JsCookieStore cookieStore,
                                         StyleSheetRegistry styleSheets,
                                         Runnable styleSheetMutationCallback) {
        return createPageRuntime(document, observer, fetchBackend, cookieStore,
                styleSheets, styleSheetMutationCallback, PageNavigationHandler.NO_OP);
    }

    public PageRuntime createPageRuntime(Document document,
                                         PageRuntimeObserver observer,
                                         JsFetchBackend fetchBackend,
                                         JsCookieStore cookieStore,
                                         StyleSheetRegistry styleSheets,
                                         Runnable styleSheetMutationCallback,
                                         PageNavigationHandler navigationHandler) {
        return new GraalPageRuntime(document, statementLimit, observer, fetchBackend, cookieStore,
                styleSheets, styleSheetMutationCallback, navigationHandler);
    }

    private static StyleSheetRegistry defaultStyleSheets(Document document) {
        StyleSheetRegistry registry = new StyleSheetRegistry();
        for (StyleSheetResource resource : new DocumentResourceScanner().scan(document).styleSheets()) {
            if (resource instanceof StyleSheetResource.Inline inline) {
                registry.register(inline.sourceOrder(), inline.element(), inline.css());
            }
        }
        return registry;
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
