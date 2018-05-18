package org.panda_lang.pandomium.wrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.*;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.CefThreadBridge;
import org.panda_lang.pandomium.util.os.PandomiumOS;

public class PandomiumThread extends Thread implements CefThreadBridge {

    private final PandomiumCEF pandomiumCEF;
    private final List<Runnable> delegates;
    protected CefApp app;
    protected CefClient client;
    protected boolean healthy;

    public PandomiumThread(PandomiumCEF pandomiumCEF) {
        super("PandomiumThread");

        this.pandomiumCEF = pandomiumCEF;
        this.delegates = new CopyOnWriteArrayList<>();
        this.healthy = true;
    }

    @Override
    public void run() {
        CefApp.setThreadBridge(this);

        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = PandomiumOS.isLinux();

        this.app = CefApp.getInstance(settings);
        this.client = app.createClient();

        while ( isHealthy() ) {
            callDelegates();
        }
    }

    public synchronized void callDelegates() {
        if ( delegates.isEmpty() ) {
            return;
        }

        List<Runnable> copy = new ArrayList<>(delegates);
        delegates.clear();

        for ( Runnable runnable : copy ) {
            try {
                if ( PandomiumOS.isLinux() ) {
                    SwingUtilities.invokeLater(runnable);
                } else {
                    runnable.run();
                }
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        }
    }

    public void dispose() {
        invokeLater(() -> {
            app.dispose();
            app.addShutdownAction(this::interrupt);
        });
    }

    @Override
    public void interrupt() {
        setHealthy(false);
        super.interrupt();
    }

    @Override
    public void invokeLater(Runnable runnable) {
        delegates.add(runnable);
    }

    @Override
    public void invokeAndWait(Runnable runnable) {
        invokeLater(runnable);

        if ( isEventDispatchThread() ) {
            callDelegates();
        } else {
            while ( delegates.size() != 0 )
                ;
        }
    }

    @Override
    public boolean isEventDispatchThread() {
        return Thread.currentThread().getId() == this.getId();
    }

    public boolean isPrepared() {
        return getApp() != null && getClient() != null;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public CefClient getClient() {
        return client;
    }

    public CefApp getApp() {
        return app;
    }

}
