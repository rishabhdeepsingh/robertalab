// Copyright (c) 2013 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.*;

import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.handler.CefAppHandler;
import org.cef.handler.CefAppHandlerAdapter;
import org.cef.util.SwingThreadBridge;
import org.slf4j.Logger;

/**
 * Exposes static methods for managing the global CEF context.
 */
public class CefApp extends CefAppHandlerAdapter {

    /**
     * According the singleton pattern, this attribute keeps one single object of
     * this class.
     */
    private static CefApp self;
    private static CefAppHandler appHandler;
    private static CefAppState state = CefAppState.NONE;
    private static CefThreadBridge cefThreadBridge = new SwingThreadBridge();

    private final Lock lock = new ReentrantLock();
    private final Condition cefShutdown = lock.newCondition();
    private Timer workTimer;
    private CefSettings settings;
    private HashSet<CefClient> clients = new HashSet<>();
    private List<Runnable> shutdownActions = new CopyOnWriteArrayList<>();
    private List<Runnable> shutdownHookActions = new CopyOnWriteArrayList<>();

    /**
     * To get an instance of this class, use the method getInstance() instead of
     * this CTOR.
     * <p>
     * The CTOR is called by getInstance() as needed and loads all required JCEF
     * libraries.
     *
     * @throws UnsatisfiedLinkError
     */
    private CefApp(String[] args, CefSettings settings) throws UnsatisfiedLinkError {
        super(args);

        if ( settings != null ) {
            this.settings = settings.clone();
        }

        if ( OS.isWindows() ) {
            System.loadLibrary("jawt");
            System.loadLibrary("chrome_elf");
            System.loadLibrary("libcef");
        } else if ( OS.isLinux() ) {
            System.loadLibrary("cef");
        }

        System.loadLibrary("jcef");

        if ( appHandler == null ) {
            appHandler = this;
        }

        // Execute on the AWT event dispatching thread.
        try {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    // Perform native pre-initialization.
                    if ( !N_PreInitialize() ) {
                        throw new IllegalStateException("Failed to pre-initialize native code");
                    }
                }
            };
            if ( cefThreadBridge.isEventDispatchThread() ) {
                r.run();
            } else {
                cefThreadBridge.invokeAndWait(r);
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        Runtime.getRuntime().addShutdownHook(new Thread("JCEF Shutdown Hook") {
            @Override
            public void run() {
                CefApp.getLogger().info("Shutdown Hook called");

                for ( Runnable action : shutdownHookActions ) {
                    action.run();
                }

                try {
                    for ( CefClient c : clients ) {
                        c.dispose();
                    }
                    // Wait for shutdown() to complete.
                    cefShutdown.awaitUninterruptibly();

                    // Avoid a deadlock. Give the native code at least 150 milliseconds
                    // to terminate.
                    Thread.sleep(150);
                } catch ( Exception e ) {
                }
            }
        });
    }

    /**
     * Assign an AppHandler to CefApp. The AppHandler can be used to evaluate
     * application arguments, to register your own schemes and to hook into the
     * shutdown sequence. See CefAppHandler for more details.
     * <p>
     * This method must be called before CefApp is initialized. CefApp will be
     * initialized automatically if you call createClient() the first time.
     *
     * @param appHandler
     *        An instance of CefAppHandler.
     * @throws IllegalStateException
     *         in case of CefApp is already initialized
     */
    public static void addAppHandler(CefAppHandler appHandler) throws IllegalStateException {
        if ( getState().compareTo(CefAppState.NEW) > 0 ) {
            throw new IllegalStateException("Must be called before CefApp is initialized");
        }
        CefApp.appHandler = appHandler;
    }

    public static Logger getLogger() {
        return CefLogger.CEF_LOGGER;
    }

    /**
     * Get an instance of this class.
     *
     * @return an instance of this class
     * @throws UnsatisfiedLinkError
     */
    public static synchronized CefApp getInstance() throws UnsatisfiedLinkError {
        return getInstance(null, null);
    }

    public static synchronized CefApp getInstance(String[] args) throws UnsatisfiedLinkError {
        return getInstance(args, null);
    }

    public static synchronized CefApp getInstance(CefSettings settings) throws UnsatisfiedLinkError {
        return getInstance(null, settings);
    }

    public static synchronized CefApp getInstance(String[] args, CefSettings settings) throws UnsatisfiedLinkError {
        if ( settings != null ) {
            if ( getState() != CefAppState.NONE && getState() != CefAppState.NEW ) {
                throw new IllegalStateException("Settings can only be passed to CEF" + " before createClient is called the first time.");
            }
        }
        if ( self == null ) {
            if ( getState() == CefAppState.TERMINATED ) {
                throw new IllegalStateException("CefApp was terminated");
            }
            self = new CefApp(args, settings);
            setState(CefAppState.NEW);
        }
        return self;
    }

    /**
     * Returns the current state of CefApp.
     *
     * @return current state.
     */
    public static CefAppState getState() {
        synchronized ( state ) {
            return state;
        }
    }

    private static void setState(final CefAppState state) {
        synchronized ( CefApp.state ) {
            CefApp.state = state;
        }
        // Execute on the AWT event dispatching thread.
        cefThreadBridge.invokeLater(new Runnable() {
            @Override
            public void run() {
                if ( appHandler != null ) {
                    appHandler.stateHasChanged(state);
                }
            }
        });
    }

    public static void setThreadBridge(CefThreadBridge threadBridge) {
        cefThreadBridge = threadBridge;
    }

    public final void setSettings(CefSettings settings) throws IllegalStateException {
        if ( getState() != CefAppState.NONE && getState() != CefAppState.NEW ) {
            throw new IllegalStateException("Settings can only be passed to CEF" + " before createClient is called the first time.");
        }
        this.settings = settings.clone();
    }

    public final CefVersion getVersion() {
        try {
            return N_GetVersion();
        } catch ( UnsatisfiedLinkError ule ) {
            ule.printStackTrace();
        }
        return null;
    }

    /**
     * To shutdown the system, it's important to call the dispose method. Calling
     * this method closes all client instances with and all browser instances each
     * client owns. After that the message loop is terminated and CEF is shutdown.
     */
    public synchronized final void dispose() {
        switch ( getState() ) {
            case NEW:
                // Nothing to do inspite of invalidating the state
                setState(CefAppState.TERMINATED);
                break;

            case INITIALIZING:
            case INITIALIZED:
                // (3) Shutdown sequence. Close all clients and continue.
                setState(CefAppState.SHUTTING_DOWN);
                if ( clients.isEmpty() ) {
                    shutdown();
                    CefApp.getLogger().info("a1");
                } else {
                    // shutdown() will be called from clientWasDisposed() when the last
                    // client is gone.
                    for ( CefClient c : clients ) {
                        c.dispose();
                    }
                }
                break;

            case NONE:
            case SHUTTING_DOWN:
            case TERMINATED:
                // Ignore shutdown, CefApp is already terminated, in shutdown progress
                // or was never created (shouldn't be possible)
                break;
        }
    }

    /**
     * Creates a new client instance and returns it to the caller. One client
     * instance is responsible for one to many browser instances
     *
     * @return a new client instance
     */
    public synchronized CefClient createClient() {
        switch ( getState() ) {
            case NEW:
                setState(CefAppState.INITIALIZING);
                initialize();
                // FALL THRU

            case INITIALIZING:
            case INITIALIZED:
                CefClient client = new CefClient();
                clients.add(client);
                return client;

            default:
                throw new IllegalStateException("Can't crate client in state " + state);
        }

    }

    /**
     * Register a scheme handler factory for the specified |scheme_name| and
     * optional |domain_name|. An empty |domain_name| value for a standard scheme
     * will cause the factory to match all domain names. The |domain_name| value
     * will be ignored for non-standard schemes. If |scheme_name| is a built-in
     * scheme and no handler is returned by |factory| then the built-in scheme
     * handler factory will be called. If |scheme_name| is a custom scheme then also
     * implement the CefApp::OnRegisterCustomSchemes() method in all processes. This
     * function may be called multiple times to change or remove the factory that
     * matches the specified |scheme_name| and optional |domain_name|. Returns false
     * if an error occurs. This function may be called on any thread in the browser
     * process.
     */
    public boolean registerSchemeHandlerFactory(String schemeName, String domainName, CefSchemeHandlerFactory factory) {
        try {
            return N_RegisterSchemeHandlerFactory(schemeName, domainName, factory);
        } catch ( Exception err ) {
            err.printStackTrace();
        }
        return false;
    }

    /**
     * Clear all registered scheme handler factories. Returns false on error. This
     * function may be called on any thread in the browser process.
     */
    public boolean clearSchemeHandlerFactories() {
        try {
            return N_ClearSchemeHandlerFactories();
        } catch ( Exception err ) {
            err.printStackTrace();
        }
        return false;
    }

    /**
     * This method is called by a CefClient if it was disposed. This causes CefApp
     * to clean up its list of available client instances. If all clients are
     * disposed, CefApp will be shutdown.
     *
     * @param client
     *        the disposed client.
     */
    final protected void clientWasDisposed(CefClient client) {
        clients.remove(client);
        if ( clients.isEmpty() && getState().compareTo(CefAppState.SHUTTING_DOWN) >= 0 ) {
            // Shutdown native system.
            shutdown();
        }
    }

    /**
     * Initialize the context.
     *
     * @return true on success.
     */
    private final void initialize() {
        // Execute on the AWT event dispatching thread.
        try {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    String library_path = getJcefLibPath();
                    CefApp.getLogger().info("Initialize on " + Thread.currentThread() + " with library path " + library_path);

                    CefSettings settings = CefApp.this.settings != null ? CefApp.this.settings : new CefSettings();

                    // Avoid to override user values by testing on NULL
                    if ( OS.isMacintosh() ) {
                        if ( settings.browser_subprocess_path == null ) {
                            Path path = Paths.get(library_path, "../Frameworks/jcef Helper.app/Contents/MacOS/jcef Helper");
                            settings.browser_subprocess_path = path.normalize().toAbsolutePath().toString();
                        }
                    } else if ( OS.isWindows() ) {
                        if ( settings.browser_subprocess_path == null ) {
                            settings.browser_subprocess_path = library_path + "\\jcef_helper.exe";
                        }
                    } else if ( OS.isLinux() ) {
                        if ( settings.browser_subprocess_path == null ) {
                            settings.browser_subprocess_path = library_path + "/jcef_helper";
                        }
                        if ( settings.resources_dir_path == null ) {
                            settings.resources_dir_path = library_path;
                        }
                        if ( settings.locales_dir_path == null ) {
                            settings.locales_dir_path = library_path + "/locales";
                        }
                    }

                    if ( N_Initialize(library_path, appHandler, settings) ) {
                        setState(CefAppState.INITIALIZED);
                    }
                }
            };

            if ( OS.isLinux() ) {
                if ( SwingUtilities.isEventDispatchThread() ) {
                    r.run();
                } else {
                    SwingUtilities.invokeAndWait(r);
                }
            } else {
                if ( cefThreadBridge.isEventDispatchThread() ) {
                    r.run();
                } else {
                    cefThreadBridge.invokeLater(r);
                    Thread.sleep(1000);
                }
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

    /**
     * This method is invoked by the native code (currently on Mac only) in case of
     * a termination event (e.g. someone pressed CMD+Q).
     */
    protected final void handleBeforeTerminate() {
        // Execute on the AWT event dispatching thread. Always call asynchronously
        // so the call stack has a chance to unwind.
        cefThreadBridge.invokeLater(new Runnable() {
            @Override
            public void run() {
                CefAppHandler handler = (CefAppHandler) ((appHandler == null) ? this : appHandler);
                if ( !handler.onBeforeTerminate() ) {
                    dispose();
                }
            }
        });
    }

    /**
     * Shut down the context.
     */
    private final void shutdown() {
        // Execute on the AWT event dispatching thread. Always call asynchronously
        // so the call stack has a chance to unwind.
        cefThreadBridge.invokeLater(new Runnable() {
            @Override
            public void run() {
                CefApp.getLogger().info("Shutdown on " + Thread.currentThread());

                // Shutdown native CEF.
                N_Shutdown();

                setState(CefAppState.TERMINATED);
                CefApp.self = null;

                // Allow the shutdown hook to terminate.
                lock.lock();
                cefShutdown.signal();
                lock.unlock();

                for ( Runnable action : shutdownActions ) {
                    action.run();
                }
            }
        });
    }

    public void shutdownNative() {
        N_Shutdown();
    }

    public void addShutdownAction(Runnable action) {
        shutdownActions.add(action);
    }

    public void addShutdownHookAction(Runnable action) {
        shutdownHookActions.add(action);
    }

    /**
     * Perform a single message loop iteration. Used on all platforms except Windows
     * with windowed rendering.
     */
    public final void doMessageLoopWork(final long delay_ms) {
        // Execute on the AWT event dispatching thread.
        cefThreadBridge.invokeLater(new Runnable() {
            @Override
            public void run() {
                if ( getState() == CefAppState.TERMINATED ) {
                    return;
                }

                // The maximum number of milliseconds we're willing to wait between
                // calls to DoMessageLoopWork().
                final long kMaxTimerDelay = 1000 / 30; // 30fps

                if ( workTimer != null ) {
                    workTimer.stop();
                    workTimer = null;
                }

                if ( delay_ms <= 0 ) {
                    // Execute the work immediately.
                    N_DoMessageLoopWork();

                    // Schedule more work later.
                    doMessageLoopWork(kMaxTimerDelay);
                } else {
                    long timer_delay_ms = delay_ms;
                    // Never wait longer than the maximum allowed time.
                    if ( timer_delay_ms > kMaxTimerDelay ) {
                        timer_delay_ms = kMaxTimerDelay;
                    }

                    workTimer = new Timer((int) timer_delay_ms, new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent evt) {
                            // Timer has timed out.
                            workTimer.stop();
                            workTimer = null;

                            N_DoMessageLoopWork();

                            // Schedule more work later.
                            doMessageLoopWork(kMaxTimerDelay);
                        }
                    });
                    workTimer.start();
                }
            }
        });
    }

    /**
     * Get the path which contains the jcef library
     *
     * @return The path to the jcef library
     */
    private String getJcefLibPath() {
        String library_path = System.getProperty("java.library.path");
        String[] paths = library_path.split(System.getProperty("path.separator"));
        for ( String path : paths ) {
            File dir = new File(path);
            String[] found = dir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return (name.equalsIgnoreCase("libjcef.dylib") || name.equalsIgnoreCase("libjcef.so") || name.equalsIgnoreCase("jcef.dll"));
                }
            });
            if ( found != null && found.length != 0 ) {
                return path;
            }
        }
        return library_path;
    }

    private final native boolean N_PreInitialize();

    private final native boolean N_Initialize(String pathToJavaDLL, CefAppHandler appHandler, CefSettings settings);

    private final native void N_Shutdown();

    private final native void N_DoMessageLoopWork();

    private final native CefVersion N_GetVersion();

    private final native boolean N_RegisterSchemeHandlerFactory(String schemeName, String domainName, CefSchemeHandlerFactory factory);

    private final native boolean N_ClearSchemeHandlerFactories();

    /**
     * The CefAppState gives you a hint if the CefApp is already usable or not
     * usable any more. See values for details.
     */
    public enum CefAppState {
        /**
         * No CefApp instance was created yet. Call getInstance() to create a new one.
         */
        NONE,

        /**
         * CefApp is new created but not initialized yet. No CefClient and no CefBrowser
         * was created until now.
         */
        NEW,

        /**
         * CefApp is in its initializing process. Please wait until initializing is
         * finished.
         */
        INITIALIZING,

        /**
         * CefApp is up and running. At least one CefClient was created and the message
         * loop is running. You can use all classes and methods of JCEF now.
         */
        INITIALIZED,

        /**
         * CefApp is in its shutdown process. All CefClients and CefBrowser instances
         * will be disposed. No new CefClient or CefBrowser is allowed to be created.
         * The message loop will be performed until all CefClients and all CefBrowsers
         * are disposed completely.
         */
        SHUTTING_DOWN,

        /**
         * CefApp is terminated and can't be used any more. You can shutdown the
         * application safely now.
         */
        TERMINATED
    }

    public final class CefVersion {
        public final int JCEF_COMMIT_NUMBER;

        public final int CEF_VERSION_MAJOR;
        public final int CEF_COMMIT_NUMBER;

        public final int CHROME_VERSION_MAJOR;
        public final int CHROME_VERSION_MINOR;
        public final int CHROME_VERSION_BUILD;
        public final int CHROME_VERSION_PATCH;

        private CefVersion(int jcefCommitNo, int cefMajor, int cefCommitNo, int chrMajor, int chrMin, int chrBuild, int chrPatch) {
            JCEF_COMMIT_NUMBER = jcefCommitNo;

            CEF_VERSION_MAJOR = cefMajor;
            CEF_COMMIT_NUMBER = cefCommitNo;

            CHROME_VERSION_MAJOR = chrMajor;
            CHROME_VERSION_MINOR = chrMin;
            CHROME_VERSION_BUILD = chrBuild;
            CHROME_VERSION_PATCH = chrPatch;
        }

        public String getJcefVersion() {
            return CEF_VERSION_MAJOR + "." + CHROME_VERSION_BUILD + "." + JCEF_COMMIT_NUMBER;
        }

        public String getCefVersion() {
            return CEF_VERSION_MAJOR + "." + CHROME_VERSION_BUILD + "." + CEF_COMMIT_NUMBER;
        }

        public String getChromeVersion() {
            return CHROME_VERSION_MAJOR + "." + CHROME_VERSION_MINOR + "." + CHROME_VERSION_BUILD + "." + CHROME_VERSION_PATCH;
        }

        @Override
        public String toString() {
            return "JCEF Version = " + getJcefVersion() + "\n" + "CEF Version = " + getCefVersion() + "\n" + "Chromium Version = " + getChromeVersion();
        }
    }
}
