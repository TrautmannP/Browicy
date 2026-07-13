package com.browicy.engine.css;

import com.browicy.engine.dom.Element;

/** Ein CSS-Selektor, der gegen ein einzelnes DOM-Element geprüft werden kann. */
public interface CssSelector {

    boolean matches(Element element);

    Specificity specificity();
}
