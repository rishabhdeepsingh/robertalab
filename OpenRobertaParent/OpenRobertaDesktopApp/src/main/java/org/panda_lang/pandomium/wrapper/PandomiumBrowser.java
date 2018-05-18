package org.panda_lang.pandomium.wrapper;

import java.awt.*;

import org.cef.browser.CefBrowser;

public class PandomiumBrowser {

    private final CefBrowser cefBrowser;

    public PandomiumBrowser(CefBrowser cefBrowser) {
        this.cefBrowser = cefBrowser;
    }

    public Component toAWTComponent() {
        return cefBrowser.getUIComponent();
    }

    public CefBrowser getCefBrowser() {
        return cefBrowser;
    }

}
