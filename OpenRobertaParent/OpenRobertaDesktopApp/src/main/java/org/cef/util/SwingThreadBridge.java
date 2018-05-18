package org.cef.util;

import java.lang.reflect.InvocationTargetException;

import javax.swing.*;

import org.cef.CefThreadBridge;

public class SwingThreadBridge implements CefThreadBridge {

    @Override
    public void invokeLater(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    @Override
    public void invokeAndWait(Runnable runnable) throws InvocationTargetException, InterruptedException {
        SwingUtilities.invokeAndWait(runnable);
    }

    @Override
    public boolean isEventDispatchThread() {
        return SwingUtilities.isEventDispatchThread();
    }

}
