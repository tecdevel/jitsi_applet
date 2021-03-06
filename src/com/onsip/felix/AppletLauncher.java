/** Copyright (c) 2013 Junction Networks **/

package com.onsip.felix;/*

 */

import java.applet.Applet;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Timer;

import netscape.javascript.JSException;
import netscape.javascript.JSObject;

import org.apache.felix.framework.util.Util;
import org.apache.felix.main.AutoProcessor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import sun.net.ProgressEvent;
import sun.net.ProgressListener;
import sun.net.ProgressSource;

import com.onsip.felix.exceptions.NoDeviceFoundException;
import com.onsip.felix.handlers.CallHandler;
import com.onsip.felix.handlers.CallPeerHandler;
import com.onsip.felix.handlers.LoadHandler;
import com.onsip.felix.handlers.RegistrationHandler;
import com.onsip.felix.privileged.Generic;
import com.onsip.felix.sources.CallEventSource;
import com.onsip.felix.sources.CallPeerEventSource;
import com.onsip.felix.sources.LoadEventSource;
import com.onsip.felix.sources.RegistrationEventSource;
import com.onsip.felix.updates.Updater;

/**
 * <p>
 * This class is the default way to instantiate and execute the framework. It is
 * not intended to be the only way to instantiate and execute the framework;
 * rather, it is one example of how to do so. When embedding the framework in a
 * host application, this class can serve as a simple guide of how to do so. It
 * may even be worthwhile to reuse some of its property handling capabilities.
 * </p>
 **/
public class AppletLauncher
    extends Applet
    implements ServiceListener, BundleListener
{
    private final static java.util.logging.Logger m_logger =
        java.util.logging.Logger.getLogger(AppletLauncher.class.getName());

    private static final String ACCOUNT_PREFIX = "com.onsip.accounts";

    private final static String JS_EVT_LOADING = "loading";
    private final static String JS_EVT_LOADED = "loaded";
    private final static String JS_EVT_UNLOADED = "unloaded";
    private final static String JS_EVT_DOWNLOAD = "download";

    private final static int PERC_CREATE_FRAMEWORK = 10;
    private final static int PERC_INIT_FRAMEWORK = 15;
    private final static int PERC_DOWNLOAD_FRAMEWORK = 20;
    private final static int PERC_START_FRAMEWORK = 60;
    private final static int PERC_SETUP_HANDLERS = 90;
    private final static int PERC_COMPLETE = 100;

    private final static int EXPECTED_REGISTERED = 37;
    private final static int EXPECTED_DOWNLOAD = 44;

    private static Map<String, Integer> DOWNLOADS_COMPLETED =
        Collections.synchronizedMap(new HashMap<String, Integer>(50));

    private static int REGISTERED_COUNTER = 0;

    private static long STORE_LAST_API_CALL = 0;

    /**
     * Client side javascript callback function
     */
    public static String JS_FN_EXPORTED = "receiveJitsiEvent";

    /**
     * Universal version identifier for a serializable class
     **/
    private static final long serialVersionUID = 7526472215622776147L;

    /**
     * The property name used to specify whether the launcher should install a
     * shutdown hook.
     **/
    public static final String SHUTDOWN_HOOK_PROP = "felix.shutdown.hook";

    /**
     * The property name used to specify an URL to the system property file.
     **/
    public static final String SYSTEM_PROPERTIES_PROP =
        "felix.system.properties";

    /**
     * The default name used for the system properties file.
     **/
    public static final String SYSTEM_PROPERTIES_FILE_VALUE =
        "system.properties";

    /**
     * The property name used to specify an URL to the configuration property
     * file to be used for the created the framework instance.
     **/
    public static final String CONFIG_PROPERTIES_PROP =
        "felix.config.properties";

    /**
     * The default name used for the configuration properties file.
     **/
    public static final String CONFIG_PROPERTIES_FILE_VALUE =
        "felix.onsip.properties";

    /**
     * This flag is flipped to true if the applet's crashed.
     * It's jitsi js that notifies the applet
     * of the crash. Subsequent loading events passed
     * passed down to the client javascript handlers
     * from the applet will include this flag.
     **/
    private static boolean RECOVERY_MODE = false;

    private static Framework m_fwk = null;

    public static String CONFIG_URL = "";

    private static JSObject win = null;

    private LoadEventSource m_loadEventSource = null;

    private int percentCompleted = 0;

    static
    {
        m_logger.setParent(java.util.logging.Logger.getLogger("com.onsip"));

        Properties props = new Properties();
        props.setProperty(".level", java.util.logging.Level.INFO.toString());

        props.setProperty("handlers",
            "java.util.logging.ConsoleHandler");

        props.setProperty("java.util.logging.ConsoleHandler.level",
            java.util.logging.Level.FINE.toString());

        props.setProperty("com.onsip.level",
            java.util.logging.Level.FINE.toString());

        props.setProperty("org.jitsi.level",
            java.util.logging.Level.SEVERE.toString());

        props.setProperty("org.jitsi.impl.level",
            java.util.logging.Level.SEVERE.toString());

        props.setProperty("net.java.sip.communicator.level",
            java.util.logging.Level.SEVERE.toString());

        props.setProperty("gov.nist",
            java.util.logging.Level.SEVERE.toString());

        try
        {
            /*
             * Initialize configuration from properties,
             * overriding any external properties file
             */
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            props.store(baos, null);
            byte[] data = baos.toByteArray();
            baos.close();
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            java.util.logging.LogManager.getLogManager().readConfiguration(bais);
        }
        catch (IOException e)
        {
            System.out.println("Error initializing log properties: " + e);
            e.printStackTrace();
        }

        /*
         *  this is the only way in which I was able to setup the formatter
         */
        try
        {
            Handler[] handlers = Logger.getLogger("").getHandlers();
            for (int index = 0; index < handlers.length; index++)
            {
                if (handlers[index] instanceof java.util.logging.ConsoleHandler)
                {
                    handlers[index].setFormatter(new com.onsip.felix.LogFormatter());
                }
            }
        }
        catch (Throwable t)
        {
            System.err.println("There was an error setting up log formatting \n");
        }

        try
        {
            m_logger.info("Check for any old storage caches");
            Updater.trash();
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE,
                "Error while trying to delete all cache stores " + e, e);
        }
    }

    @Override
    public void init()
    {
        try
        {
            try
            {
                win = JSObject.getWindow(this);
            }
            catch (JSException jse)
            {
                m_logger.log(Level.SEVERE, "JSException :: init : ");
                m_logger.log(Level.SEVERE,
                    "Can deliver messages to the javascript client", jse);
            }

            m_loadEventSource = new LoadEventSource();
            m_loadEventSource.addEventListener(new LoadHandler());

            m_loadEventSource.fireEvent(
                new String[]
                   { this.getSerializedEvent
                        (JS_EVT_UNLOADED, "init applet", 0) });

            m_logger.log(Level.INFO, "**************** Environment *************");
            m_logger.log(Level.INFO, "OS name: " +
                System.getProperty("os.name"));
            m_logger.log(Level.INFO, "OS architecture: " +
                System.getProperty("os.arch"));
            m_logger.log(Level.INFO, "OS version: " +
                System.getProperty("os.version"));
            m_logger.log(Level.INFO, "Java version: " +
                System.getProperty("java.version"));
            m_logger.log(Level.INFO, "tmpdir: " +
                System.getProperty("java.io.tmpdir"));
            m_logger.log(Level.INFO, "deployment cache dir: " +
                System.getProperty("deployment.user.cachedir"));
            m_logger.log(Level.INFO, "system cache dir: " +
                System.getProperty("deployment.system.cachedir"));
            m_logger.log(Level.INFO, "**************** ********** *************");

            Object a2m =
                Class.forName("sun.plugin2.applet.Applet2ClassLoader");

            try
            {
                if (a2m != null){
                    @SuppressWarnings("rawtypes")
                    Class[] c = new Class[] { boolean.class };
                    Method m = this.getClass().getClassLoader().
                        getClass().getMethod("setCodebaseLookup", c);
                    m.invoke(this.getClass().getClassLoader(),
                        new Object[]{ false });
                }
            }
            catch (Exception ex)
            {
                m_logger.log(Level.WARNING, "Exception :: init : ");
                m_logger.log(Level.WARNING,
                    "Failed on setting codebase lookup property, ignoring", ex);
            }

            /**
             * Felix will throw an exception if NIO has unsuccessfully acquired
             * a lock on the "cache.lock" file in the Felix cache directory.
             * In a browser environment, this exception manifests itself when
             * loading the applet twice in the same browser process. For some
             * reason, this error doesn't occur when loading the applet
             * in two separate browsers. My guess is that a second browser
             * means a separate Java VM.
             * Baring this knowledge we take preemptive action to notify
             * clients that a second instance of the applet is about to load
             * but will fail. We can notify the javascript layer, and then
             * kill our applet. Consuming clients will need to take appropriate
             * steps to notify the end user and then force their application
             * to reload.
             *
             */
            if (Updater.isCacheLocked())
            {
                m_logger.log(Level.WARNING,
                    "Felix Cache file is locked, meaning phone is running in a " +
                        "separate tab");

                sendError(new Exception("Phone is already running"), "reload");

                Timer tShutdown = new Timer(2000, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent evt) {
                        System.exit(0);
                    }
                });

                tShutdown.setRepeats(false);
                tShutdown.start();

                return;
            }

            setupDownloadMonitoring();

            initFramework(new String[] {});
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: init : ");
            m_logger.log(Level.SEVERE,
                "create Framework didn't complete successfully", e);
            sendError(e);
        }
    }

    @Override
    public void stop()
    {
        Runtime.getRuntime().exit(0);
    }

    @Override
    public void destroy()
    {
        System.exit(0);
    }

    private void setupDownloadMonitoring()
    {
        try
        {
            /*
             * Setup progress monitoring on jar bundle downloads
             */
            sun.plugin.util.ProgressMonitor progressMonitor =
                (sun.plugin.util.ProgressMonitor)
                    sun.net.ProgressMonitor.getDefault();

            progressMonitor.addProgressListener(
                Thread.currentThread().getThreadGroup(),
                    new ProgressListener() {
                        @Override
                        public void progressFinish(ProgressEvent e)
                        {
                            if (REGISTERED_COUNTER == 0 && percentCompleted < 100)
                            {
                                m_loadEventSource.fireEvent(
                                    new String[]
                                               { getSerializedDownloadEvent(e) });
                                m_logger.log(Level.FINE, "Download finished " + e);
                            }
                        }

                        @Override
                        public void progressStart(ProgressEvent e)
                        {
                            if (REGISTERED_COUNTER == 0 && percentCompleted < 100)
                            {
                                m_loadEventSource.fireEvent(
                                    new String[]
                                               { getSerializedDownloadEvent(e) });
                                m_logger.log(Level.FINE, "Download started " + e);
                            }
                        }

                        @Override
                        public void progressUpdate(ProgressEvent e)
                        {
                            if (REGISTERED_COUNTER == 0 && percentCompleted < 100)
                            {
                                m_loadEventSource.fireEvent(
                                    new String[]
                                               { getSerializedDownloadEvent(e) });
                                m_logger.log(Level.FINE, "Download updated " + e);
                            }
                        }
                    });
        }
        catch (Exception e)
        {
            /* Monitor the progress of bundle downloads */
            m_logger.log(Level.SEVERE,
                "Exception :: setupDownloadMonitoring : ");
            m_logger.log(Level.SEVERE,
                "Error initializing Progress Monitor: " + e, e);
        }
    }

    private static String getFelixCacheDir() throws Exception
    {
        String dir = System.getProperty("deployment.user.cachedir");
        if (dir == null) {
            dir = System.getProperty("user.home");
        }
        m_logger.log(Level.FINE, "Using felix cache dir - " + dir);
        File f = new File(dir);
        if (f.exists() && f.isDirectory())
        {
            String separator = System.getProperty("file.separator");
            return (dir + separator + "felix-cache");
        }
        /* This error should never be thrown */
        throw new Exception("Felix Cache Directory not set in applet");
    }

    /* Client side javascript sink */
    private void setJsCallbackFn()
    {
        try
        {
            String cb = this.getParameter("callback");
            if (cb != null && cb.length() > 0)
            {
                System.setProperty("onsip.js.callback", cb);
                JS_FN_EXPORTED = cb;
            }
            System.setProperty(
                "net.java.sip.communicator.packetlogging.PACKET_LOGGING_ENABLED",
                "false");
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE,
                "Exception :: setJsCallbackFn : ");
            m_logger.log(Level.SEVERE,
                "We couldn't set the javascript callback function, " +
                "we'll default to [" + JS_FN_EXPORTED + "]," +
                "error details " + e, e);
        }
    }

    private void setupJitsiEnv()
    {
        try
        {
            System.setProperty(
                "net.sf.fmj.utility.JmfRegistry.disableLoad",
                    "false");

            String scHomeDir =
                System.getProperty("deployment.user.cachedir");

            if (scHomeDir == null)
            {
                scHomeDir = System.getProperty("user.home");
            }

            System.setProperty(
                "net.java.sip.communicator.SC_HOME_DIR_LOCATION",
                    scHomeDir);

            System.setProperty(
                "net.java.sip.communicator.PNAME_SC_HOME_DIR_LOCATION",
                    scHomeDir);

            System.setProperty(
                "net.java.sip.communicator.SC_HOME_DIR_NAME",
                    ".sip-communicator");

            System.setProperty(
                "net.java.sip.communicator.PNAME_SC_HOME_DIR_NAME",
                    ".sip-communicator");

            System.setProperty(
                "net.java.sip.communicator.service.media.DISABLE_VIDEO_SUPPORT",
                    "true");
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE,
                "Exception :: setupJitsiEnv : ");
            m_logger.log(Level.SEVERE,
                "We couldn't set requisite jitsi props  " +
                "(eg. default folder locations) ,error details " + e, e);
        }
    }

    /* Allow applet to specify debugging options */
    private void setLogging()
    {
        /**
         * Initially, we want to turn off all logging
         * related to features we don't care about (e.g. Jabber)
         */
        try
        {
            // turn off regardless
            System.setProperty(
                "net.java.sip.communicator.packetlogging.PACKET_LOGGING_ENABLED",
                "false");
            System.setProperty(
                "net.java.sip.communicator.packetlogging.PACKET_LOGGING_JABBER_ENABLED",
                "false");
            System.setProperty(
                "net.java.sip.communicator.packetlogging.PACKET_LOGGING_ICE4J_ENABLED",
                "false");
            System.setProperty(
                "net.java.sip.communicator.packetlogging.PACKET_LOGGING_SIP_ENABLED",
                "false");
            System.setProperty(
                "net.java.sip.communicator.packetlogging.PACKET_LOGGING_RTP_ENABLED",
                "false");
        }
        catch(Exception ex)
        {
            // shouldn't fail, but ignore regardless
        }
        try
        {
            String debug = this.getParameter("applet_debug_level");
            int debugLevel = 0;

            if (debug != null && debug.length() > 0)
            {
                try
                {
                    debugLevel = Integer.parseInt(debug);
                }
                catch(NumberFormatException ex)
                {
                    debugLevel = 0;
                }

                System.out.println("Setting logging level to " + debugLevel);

                if (debugLevel > 0 && debugLevel <= 4)
                {
                    m_logger.setParent(java.util.logging.Logger.getLogger("com.onsip"));

                    Properties props = new Properties();
                    props.setProperty(".level", java.util.logging.Level.INFO.toString());

                    props.setProperty("handlers",
                        "java.util.logging.ConsoleHandler");

                    props.setProperty("java.util.logging.ConsoleHandler.level",
                        java.util.logging.Level.FINE.toString());

                    props.setProperty("com.onsip.level",
                        java.util.logging.Level.SEVERE.toString());

                    props.setProperty("org.jitsi.level",
                        java.util.logging.Level.SEVERE.toString());

                    props.setProperty("org.jitsi.impl.level",
                        java.util.logging.Level.SEVERE.toString());

                    props.setProperty("net.java.sip.communicator.level",
                        java.util.logging.Level.SEVERE.toString());

                    props.setProperty("gov.nist",
                        java.util.logging.Level.SEVERE.toString());

                    if (debugLevel >= 1)
                    {
                        props.setProperty("com.onsip.level",
                            java.util.logging.Level.FINE.toString());

                        if (debugLevel >= 2)
                        {
                            props.setProperty("org.jitsi.impl.level",
                                java.util.logging.Level.FINE.toString());

                            if (debugLevel >= 3)
                            {
                                props.setProperty("net.java.sip.communicator.level",
                                    java.util.logging.Level.FINE.toString());

                                props.setProperty("org.jitsi.level",
                                    java.util.logging.Level.FINE.toString());

                                props.setProperty("net.java.sip.communicator.impl.netaddr.level",
                                    java.util.logging.Level.SEVERE.toString());

                                System.setProperty(
                                    "net.java.sip.communicator.packetlogging.PACKET_LOGGING_SIP_ENABLED",
                                    "true");
                                System.setProperty(
                                    "net.java.sip.communicator.packetlogging.PACKET_LOGGING_RTP_ENABLED",
                                    "true");

                                if (debugLevel >= 4)
                                {
                                    props.setProperty("gov.nist",
                                        java.util.logging.Level.FINE.toString());

                                    props.setProperty("net.java.sip.communicator.impl.netaddr.level",
                                        java.util.logging.Level.FINE.toString());
                                }
                            }
                        }
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    props.store(baos, null);
                    byte[] data = baos.toByteArray();
                    baos.close();
                    ByteArrayInputStream bais = new ByteArrayInputStream(data);
                    java.util.logging.LogManager.getLogManager().readConfiguration(bais);
                }
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.WARNING,
                "Exception :: setLogging : ");
            m_logger.log(Level.WARNING,
                "We couldn't set the javascript debug option, " +
                "we'll default to turning off all logging," +
                "error details " + e, e);
        }
    }

    private void setRecoveryModeFlag()
    {
        try
        {
            String cb = this.getParameter("recover");
            RECOVERY_MODE = (cb != null && cb.toLowerCase().trim().equals("true"));
        }
        catch(Exception e)
        {
            RECOVERY_MODE = false;
            m_logger.log(Level.SEVERE,
                "Exception :: reRegister : ");
            m_logger.log(Level.SEVERE,
                "We couldn't access param callback for recover, " +
                "error details " + e, e);
        }
    }

    private synchronized void setProxyFromParams()
    {
        try
        {
            String proxyAddress = this.getParameter("proxy_address");
            if (proxyAddress != null)
            {
                proxyAddress = proxyAddress.trim();
                if (proxyAddress.length() > 0)
                {
                    System.setProperty(ACCOUNT_PREFIX +
                        "PROXY_ADDRESS", proxyAddress);
                }
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE,
                "Exception :: setProxyFromParams : ");
            m_logger.log(Level.SEVERE,
                "Error while setting proxy, details " + e, e);
        }
    }

    private void setPropertiesConfigFile()
    {
        try
        {
            System.setProperty(CONFIG_PROPERTIES_PROP,
                CONFIG_PROPERTIES_FILE_VALUE);
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE,
                "Exception :: setPropertiesConfigFile : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    public void initFramework(String[] args) throws Exception
    {
        /* Set the javascript callback function */
        setJsCallbackFn();

        /* Proxy details passed through embed parameters */
        setProxyFromParams();

        /* Setup logging options */
        setLogging();

        /* Setup Jitsi specific props **/
        setupJitsiEnv();

        /* Did the applet crash, and are we recovering from it */
        setRecoveryModeFlag();

        try
        {
            /* Set the location of the felix properties file */
            setPropertiesConfigFile();

            /* Check if this platform can run the applet */
            Capabilities.isPlatformSupported();

            /* Load system properties. */
            loadSystemProperties();

            /* Read configuration properties. */
            Properties configProps = loadConfigProperties();

            /* Copy framework properties from the system properties. */
            copySystemProperties(configProps);

            /* Check for updated jars */
            Updater.checkUpdates(configProps);

            /*
             * If enabled, register a shutdown hook to make sure the framework is
             * cleanly shutdown when the VM exits.
             */
            String enableHook = configProps.getProperty(SHUTDOWN_HOOK_PROP);
            if ((enableHook == null) || !enableHook.equalsIgnoreCase("false"))
            {
                Runtime.getRuntime().addShutdownHook(
                    new Thread("Felix Shutdown Hook")
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                if (m_fwk != null)
                                {
                                    m_logger.log(Level.FINE,
                                        "Shutdown called from AppletLauncher " +
                                            new Date().toString());
                                    m_fwk.stop();
                                    m_fwk.waitForStop(0);
                                }
                            }
                            catch (Exception ex)
                            {
                                m_logger.log(Level.SEVERE,
                                        "Error stopping framework: ", ex);
                            }
                        }
                    });
            }

            /* 10% Complete */
            m_loadEventSource.fireEvent(
                new String[]
                   { this.getSerializedEvent
                        (JS_EVT_LOADING, "create new framework",
                            PERC_CREATE_FRAMEWORK) });


            /* Create an instance of the framework. */
            FrameworkFactory factory = getFrameworkFactory();
            m_fwk = factory.newFramework(configProps);

            /* 15% Complete */
            m_loadEventSource.fireEvent(
                new String[]
                   { this.getSerializedEvent
                        (JS_EVT_LOADING, "init framework",
                            PERC_INIT_FRAMEWORK) });

            /* Initialize the framework, but don't start it yet. */
            m_fwk.init();
            m_fwk.getBundleContext().addBundleListener(this);

            /* 20% Complete */
            m_loadEventSource.fireEvent(
                new String[]
                   { this.getSerializedEvent
                        (JS_EVT_LOADING, "download framework bundles",
                            PERC_DOWNLOAD_FRAMEWORK) });

            /*
             * Use the system bundle context to process the auto-deploy and
             * auto-install/auto-start properties.
             */
            AutoProcessor.process(configProps, m_fwk.getBundleContext());

            m_fwk.getBundleContext().addServiceListener(this);

            /* 60% Complete */
            m_loadEventSource.fireEvent(
                new String[]
                   { this.getSerializedEvent
                        (JS_EVT_LOADING, "start framework bundles",
                            PERC_START_FRAMEWORK) });

            /* Start the framework. */
            m_fwk.start();

            /* 90% Complete */
            m_loadEventSource.fireEvent(
                new String[]
                   { this.getSerializedEvent
                        (JS_EVT_LOADING,
                            "setup event handlers in activator",
                                PERC_SETUP_HANDLERS) });

            m_logger.log(Level.INFO, "Init Handlers");
            initHandlers();

            int delay = 500;
            ActionListener taskPerformer = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent evt) {
                    /* 100% Complete */
                    m_loadEventSource.fireEvent(
                        new String[]
                           { getSerializedEvent(JS_EVT_LOADED,
                               "done", PERC_COMPLETE) });
                }
            };

            javax.swing.Timer t = new javax.swing.Timer(delay, taskPerformer);
            t.setRepeats(false);
            t.start();

            /*
             * if this is a fresh install, we need to create the
             * cache.version file. We do it here because the
             * assumption is that the felix cache folder
             * exists at this point
             */
            Updater.setCacheVersion(configProps);

            /* make sure no more loading events are sent to the client */
            REGISTERED_COUNTER = EXPECTED_REGISTERED;

            /* Check the availability of input devices */
            Capabilities.getDefaultMicrophone();
        }
        catch (NoDeviceFoundException ndfe)
        {
            m_logger.log(Level.WARNING, "Could not find input device ", ndfe);
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE,
                "Could not load the entirety of the framework bundles: ", e);
            sendError(new Exception("Could not load the phone"));
        }
    }

    /**
     * Simple method to parse META-INF/services file for framework factory.
     * Currently, it assumes the first non-commented line is the class name of
     * the framework factory implementation.
     *
     * @return The created <tt>FrameworkFactory</tt> instance.
     * @throws Exception if any errors occur.
     **/
    private static FrameworkFactory getFrameworkFactory()
        throws Exception
    {
        try
        {
            URL url =
                AppletLauncher.class.getClassLoader().getResource(
                    "META-INF/services/org.osgi.framework.launch.FrameworkFactory");
            if (url != null)
            {
                BufferedReader br =
                    new BufferedReader(new InputStreamReader(url.openStream()));
                try
                {
                    for (String s = br.readLine(); s != null; s = br.readLine())
                    {
                        s = s.trim();
                        /* Try to load first non-empty, non-commented line. */
                        if ((s.length() > 0) && (s.charAt(0) != '#'))
                        {
                            return (FrameworkFactory) Class.forName(s)
                                .newInstance();
                        }
                    }
                }
                finally
                {
                    if (br != null)
                        br.close();
                }
            }
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: getFrameworkFactory : ");
            m_logger.log(Level.SEVERE,
                "Could not load framework factory, details " +
                    e.getMessage(), e);
            throw e;
        }
        throw new Exception("Could not load " +
            FrameworkFactory.class.toString());
    }

    /**
     * <p>
     * Loads the properties in the system property file associated with the
     * framework installation into <tt>System.setProperty()</tt>. These
     * properties are not directly used by the framework in anyway. By default,
     * the system property file is located in the <tt>conf/</tt> directory of
     * the Felix installation directory and is called "
     * <tt>system.properties</tt>". The installation directory of Felix is
     * assumed to be the parent directory of the <tt>felix.jar</tt> file as
     * found on the system class path property. The precise file from which to
     * load system properties can be set by initializing the "
     * <tt>felix.system.properties</tt>" system property to an arbitrary URL.
     * </p>
     **/
    protected static void loadSystemProperties()
        throws Exception
    {
        try
        {
            /* See if the property URL was specified as a property. */
            URL propURL = null;
            String custom = System.getProperty(SYSTEM_PROPERTIES_PROP);
            if (custom == null)
            {
               return;
            }
            try
            {
                propURL = new URL(custom);
            }
            catch (MalformedURLException mue)
            {
                m_logger.log(Level.SEVERE,
                    "MalformedURLException :: loadSystemProperties : ");
                m_logger.log(Level.SEVERE, mue.getMessage(), mue);
                return;
            }

            /* Read the properties file. */
            Properties props = new Properties();
            InputStream is = null;
            try
            {
                is = propURL.openConnection().getInputStream();
                props.load(is);
                is.close();
            }
            catch (FileNotFoundException ex)
            {
                // Ignore file not found.
            }
            catch (Exception e)
            {
                m_logger.log(Level.SEVERE,
                    "Exception :: loadSystemProperties : ");
                m_logger.log(Level.SEVERE,
                    "Error loading system properties from " + propURL +
                        ", details " + e.getMessage(), e);
                try
                {
                    if (is != null)
                        is.close();
                }
                catch (IOException ex2)
                {
                    // Nothing we can do.
                }

                return;
            }

            /* Perform variable substitution on specified properties. */
            @SuppressWarnings("unchecked")
            Enumeration<String> e = (Enumeration<String>) props.propertyNames();
            while (e.hasMoreElements())
            {
                String name = e.nextElement();
                System.setProperty(name,
                    Util.substVars(props.getProperty(name), name, null, null));
            }
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE,
                "Exception :: loadSystemProperties : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * <p>
     * Loads the configuration properties in the configuration property file
     * associated with the framework installation; these properties are
     * accessible to the framework and to bundles and are intended for
     * configuration purposes. By default, the configuration property file is
     * located in the <tt>conf/</tt> directory of the Felix installation
     * directory and is called "<tt>config.properties</tt>". The installation
     * directory of Felix is assumed to be the parent directory of the
     * <tt>felix.jar</tt> file as found on the system class path property. The
     * precise file from which to load configuration properties can be set by
     * initializing the " <tt>felix.config.properties</tt>" system property to
     * an arbitrary URL.
     * </p>
     *
     * @return A <tt>Properties</tt> instance or <tt>null</tt> if there was an
     *         error.
     * @throws Exception
     **/
    protected static Properties loadConfigProperties()
        throws Exception
    {
        Properties props = null;
        try
        {
            /* See if the property URL was specified as a property.*/
            URL propURL = null;
            String custom = System.getProperty(CONFIG_PROPERTIES_PROP);
            if (custom == null)
            {
                throw new Exception(CONFIG_PROPERTIES_FILE_VALUE +
                    " not found.");
            }
            try
            {
                //propURL = new URL(custom);
                propURL =
                    AppletLauncher.class.getClassLoader().getResource(
                        "META-INF/felix.onsip.properties");
            }
            catch (Exception ex)
            {
                throw new Exception("Could not load property file " +
                    CONFIG_PROPERTIES_FILE_VALUE);
            }

            /* Read the properties file. */
            props = new Properties();
            InputStream is = null;
            try
            {
                /* Try to load config.properties. */
                is = propURL.openConnection().getInputStream();
                props.load(is);
                is.close();
            }
            catch (Exception ex)
            {
                /* Try to close input stream if we have one. */
                try
                {
                    if (is != null)
                        is.close();
                }
                catch (IOException ex2)
                {
                    /* Nothing we can do. */
                }
                throw new Exception("No " + CONFIG_PROPERTIES_FILE_VALUE + " found.");
            }


            /* Perform variable substitution for system properties. */
            @SuppressWarnings("unchecked")
            Enumeration<String> e = (Enumeration<String>) props.propertyNames();
            while (e.hasMoreElements())
            {
                String name = e.nextElement();
                props.setProperty(name,
                    Util.substVars(props.getProperty(name), name, null, props));
            }

            String felixCacheDir = getFelixCacheDir();
            props.setProperty("org.osgi.framework.storage", felixCacheDir);
            props.setProperty("felix.cache.rootdir", felixCacheDir);
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE,
                "Exception :: loadConfigProperties : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
            throw e;
        }
        return props;
    }

    protected static void copySystemProperties(Properties configProps)
        throws Exception
    {
        try
        {
            @SuppressWarnings("unchecked")
            Enumeration<String> e =
                (Enumeration<String>) System.getProperties().propertyNames();
            while (e.hasMoreElements())
            {
                String key = e.nextElement();
                if (key.startsWith("felix.")
                    || key.startsWith("org.osgi.framework."))
                {
                    configProps.setProperty(key, System.getProperty(key));
                }
            }
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE,
                "Exception :: copySystemProperties : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
            throw e;
        }
    }

    public static Framework getFramework()
    {
        return m_fwk;
    }

    public static Object getService()
    {
        try
        {
            BundleContext context = getFramework().getBundleContext();

            ServiceReference sref =
                context.getServiceReference
                    ("com.onsip.communicator.impl.applet.AppletActivator");

            Object service = context.getService(sref);

            return service;
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: getService : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }

    public static JSObject getJSObject()
    {
        return win;
    }

    /**
     * The Event Source objects (registrationSource, callEventSource,
     * callPeerEventSource) are passed on to our Jitsi Wrapper
     * (aka, AppletActivator). These objects are called upon from within
     * Jitsi to notify the applet of events that
     * need to be passed on to the browser. The event listeners
     * (i.e. CallHandler & RegistrationHandler) will be the recipients
     * of said events. They are responsible for repackaging and delivering
     * the relevant events down to the browser.
     */
    public void initHandlers()
    {
        try
        {
            RegistrationEventSource registrationSource = new RegistrationEventSource();
            registrationSource.addEventListener(new RegistrationHandler());

            CallEventSource callEventSource = new CallEventSource();
            callEventSource.addEventListener(new CallHandler());

            CallPeerEventSource callPeerEventSource = new CallPeerEventSource();
            callPeerEventSource.addEventListener(new CallPeerHandler());

            Object service = getService();
            Class<? extends Object> clazz = service.getClass();

            // For Registration State Change Events
            Method m = clazz.getMethod("setRegistrationEventSource",
                new Class[] { Object.class });

            m.invoke(service, registrationSource);

            // For Call Events
            m = clazz.getMethod("setCallEventSource",
                new Class[] { Object.class });

            m.invoke(service, callEventSource);

            // For Call Peer Events
            m = clazz.getMethod("setCallPeerEventSource",
                new Class[] { Object.class });

            m.invoke(service, callPeerEventSource);
        }
        catch (SecurityException e)
        {
            m_logger.log(Level.SEVERE, "SecurityException :: initHandlers : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        catch (NoSuchMethodException e)
        {
            m_logger.log(Level.SEVERE, "NoSuchMethodException :: initHandlers : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        catch (IllegalArgumentException e)
        {
            m_logger.log(Level.SEVERE, "IllegalArgumentException :: initHandlers : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        catch (IllegalAccessException e)
        {
            m_logger.log(Level.SEVERE, "IllegalAccessException :: initHandlers : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        catch (InvocationTargetException e)
        {
            m_logger.log(Level.SEVERE, "InvocationTargetException :: initHandlers : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: initHandlers : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /**
     * Currently the only exported OnSIP function
     *
     * @param fnName the function to call
     * @param args arguments
     * @return
     */
    public synchronized boolean api(final String fnName, final String [] args)
    {
        try
        {
            if (fnName == null || args == null) return false;

            boolean isEcho = fnName.equalsIgnoreCase("echo");
            if (!isEcho)
            {
                m_logger.log(Level.FINE, "In api call " +
                    fnName + " with " + args.length + " # of args");
            }

            /**
             * Don't print private details associated with registering
             */
            for (int i=0; i < args.length ; i++)
            {
                if (!fnName.equalsIgnoreCase("register") && !isEcho)
                {
                    m_logger.log(Level.FINE, "arg " + i + " => " + args[i]);
                }
            }

            /**
             * Echo functionality added as a way to verify that the
             * applet is still running
             */
            if (fnName.equalsIgnoreCase("echo"))
            {
                String msg = (args.length > 0 && args[0] != null) ? args[0] : "";
                m_loadEventSource.fireEvent(
                    new String[]
                       { getSerializedEvent("echo", msg, percentCompleted) });
                return true;
            }

            /**
             * Don't allow consuming js clients to start making api calls
             * if the phone's bundles have not finished loading
             */
            if (percentCompleted < 100)
            {
                m_loadEventSource.fireEvent(
                    new String[]
                       { getSerializedEvent(JS_EVT_LOADING,
                           "web phone has not finished loading",
                               percentCompleted) });
                return true;
            }

            /**
             * This bit of functionality is meant to throttle
             * consecutive calls to the api by consuming
             * javascript clients
             */
            int delay = 0; //milliseconds

            long current =  System.currentTimeMillis();
            long tmpDelay = current - STORE_LAST_API_CALL;
            if (tmpDelay < 500)
            {
                // delay ~ 1/2 sec
                delay = 500 + ((int) Math.round(Math.random() * 100));
                m_logger.log(Level.FINE, "Throttle api call " + delay + " millis");
            }

            STORE_LAST_API_CALL = current;

            ActionListener apiCallTask = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent evt) {
                    AccessController.doPrivileged(new Generic(fnName, args));
                }
            };

            Timer tCallApi = new Timer(delay, apiCallTask);
            tCallApi.setRepeats(false);
            tCallApi.start();

            return true;
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: api : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return false;
    }

    @Override
    public void serviceChanged(ServiceEvent event)
    {
        try
        {
            String[] objectClass =
                (String[]) event.getServiceReference().getProperty("objectClass");

            if (event.getType() == ServiceEvent.REGISTERED)
            {
                m_logger.log(Level.FINE, "Service of type "
                    + objectClass[0] + " REGISTERED. ");

                if (REGISTERED_COUNTER < EXPECTED_REGISTERED)
                {
                    REGISTERED_COUNTER++;
                    double counter = REGISTERED_COUNTER;
                    double total = EXPECTED_REGISTERED;
                    double percent = (counter / total) * 100.0;
                    percent =
                        ((PERC_SETUP_HANDLERS - PERC_START_FRAMEWORK) * percent)
                            / 100;
                    percent = PERC_START_FRAMEWORK + percent;
                    if (percent > PERC_SETUP_HANDLERS)
                    {
                        percent = PERC_SETUP_HANDLERS;
                    }

                    int percentAbs = (int) Math.floor(percent);

                    m_loadEventSource.fireEvent(
                        new String[]
                           { this.getSerializedEvent
                                (JS_EVT_LOADING, "registering bundle " +
                                    objectClass[0], percentAbs) });
                }
            }
            else if (event.getType() == ServiceEvent.UNREGISTERING)
            {
                m_logger.log(Level.FINE, "Service of type "
                    + objectClass[0] + " UNREGISTERING.");
            }
            else if (event.getType() == ServiceEvent.MODIFIED)
            {
                m_logger.log(Level.FINE, "Service of type "
                    + objectClass[0] + " MODIFIED.");
            }
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: serviceChanged : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    @Override
    public void bundleChanged(BundleEvent bundle)
    {
        try
        {
            int state = bundle.getBundle().getState();
            String stateAlias = "UNKOWN";
            switch (state)
            {
            case 1:
                stateAlias = "INSTALLED";
                break;
            case 2:
                stateAlias = "STARTED";
                break;
            case 4:
                stateAlias = "STOPPED";
                break;
            case 8:
                stateAlias = "UPDATED";
                break;
            case 10:
                stateAlias = "UNINSTALLED";
                break;
            case 20:
                stateAlias = "RESOLVED";
                break;
            case 40:
                stateAlias = "UNRESOLVED";
                break;
            case 80:
                stateAlias = "STARTING";
                break;
            case 100:
                stateAlias = "STOPPING";
                break;
            case 200:
                stateAlias = "LAZY_ACTIVATION";
                break;
            case 32:
                stateAlias = "RENAME_FILE_ACTION";
                break;
            default:
                stateAlias +=  " (" + state + ") ";
            }

            String l = "Bundle " + bundle.getBundle().getLocation() + "; ";
            if (bundle.getBundle().getVersion() != null)
            {
                l += "version: " + bundle.getBundle().getVersion().toString() + "; ";
                l += "bundle id: " + bundle.getBundle().getBundleId() + "; ";
            }
            l += " is in state " + stateAlias;
            m_logger.log(Level.FINE, l);
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: bundleChanged : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private void sendError(Throwable t)
    {
        try
        {
            String json = getSerializedEventError(t);
            m_loadEventSource.fireEvent(new String[] { json });
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: sendError :");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private void sendError(Throwable t, String actionToTake)
    {
        try
        {
            String json = getSerializedEventError(t, actionToTake);
            m_logger.log(Level.INFO, json);
            m_loadEventSource.fireEvent(new String[] { json });
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: sendError :");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /**
     *
     * @param state unloaded, loading, loaded
     * @param progress progress percentage
     *
     * @return serialized json object
     */
    public String getSerializedEvent(String state, String msg, double progress)
    {
        percentCompleted = (int) Math.abs(progress);
        return '{' + "\"package\":\"loader\",\"type\":\"" +
            state + "\",\"details\":{\"progress\":\"" +
                percentCompleted + "\",\"message\":\"" +
                    msg + "\"," + "\"recovery\":\"" +
                        RECOVERY_MODE + "\"}" + '}';
    }

    private static String getSerializedDownloadEvent(ProgressEvent e)
    {
        String state = "";
        if (e.getState() == ProgressSource.State.NEW)
        {
            state = "started";
        }
        else if (e.getState() == ProgressSource.State.UPDATE)
        {
            state = "downloading";
        }
        else if (e.getState() == ProgressSource.State.DELETE)
        {
            if (e.getURL() != null)
            {
                String urlHcKey = e.getURL().toExternalForm();
                if (urlHcKey != null && urlHcKey.length() > 0);
                {
                    String jarExt = urlHcKey.substring(urlHcKey.length() - 4);
                    if (jarExt.equalsIgnoreCase(".jar"))
                    {
                        int idx = urlHcKey.lastIndexOf('/');
                        if (idx > -1)
                        {
                            urlHcKey = urlHcKey.substring(idx + 1);
                        }

                        if (!AppletLauncher.DOWNLOADS_COMPLETED.containsKey(urlHcKey))
                        {
                            m_logger.info("");
                            m_logger.info("Downloaded: " + urlHcKey);
                            m_logger.info("");
                            AppletLauncher.DOWNLOADS_COMPLETED.put(urlHcKey, 0);

                            double downloadCount = AppletLauncher.DOWNLOADS_COMPLETED.size();
                            double expectedCount = AppletLauncher.EXPECTED_DOWNLOAD;
                            double progress = (downloadCount / expectedCount);

                            double percent =
                                (((PERC_START_FRAMEWORK - PERC_DOWNLOAD_FRAMEWORK) * progress)) +
                                    PERC_DOWNLOAD_FRAMEWORK;

                            long percentCompleted = Math.round(percent);

                            if (percentCompleted > PERC_START_FRAMEWORK)
                            {
                                percentCompleted = PERC_START_FRAMEWORK;
                            }

                            String msg = "download framework bundle " + e.getURL().toExternalForm();

                            String json = '{' + "\"package\":\"loader\",\"type\":\"" +
                                JS_EVT_LOADING + "\",\"details\":{\"progress\":\"" +
                                    percentCompleted + "\",\"message\":\"" +
                                        msg + "\"," + "\"recovery\":\"" +
                                            RECOVERY_MODE + "\"}" + '}';

                            if (downloadCount > 2)
                            {
                                m_logger.info(json);
                                return json;
                            }
                        }
                    }
                }
                state = "complete";
            }
        }
        else if (e.getState() == ProgressSource.State.CONNECTED)
        {
            state = "connected";
        }

        double downloadCount = AppletLauncher.DOWNLOADS_COMPLETED.size();
        double expectedCount = AppletLauncher.EXPECTED_DOWNLOAD;
        double progress = (downloadCount / expectedCount) * 100;

        if (progress > 100)
        {
            progress = 100;
        }

        String json =  '{' + "\"package\":\"loader\",\"type\":\"" +
            JS_EVT_DOWNLOAD + "\",\"details\":{\"progress\":\"" +
                ((int) Math.floor(progress)) + "\",\"url\":\"" +
                    e.getURL().toExternalForm() +
                        "\", \"message\":\"" +
                            state + "\"}" + '}';

        m_logger.info(json);

        return json;
    }

    private String getSerializedEventError(Throwable t)
    {
        return '{' + "\"package\":\"loader\",\"type\":\"error\"," +
            "\"details\":{\"message\":\"" + t.getMessage() + "\"}" + '}';
    }

    private String getSerializedEventError(Throwable t, String actionToTake)
    {
        return '{' + "\"package\":\"loader\",\"type\":\"error\"," +
            "\"details\":{\"message\":\"" + t.getMessage() + "\"," +
                "\"action\":\"" + actionToTake + "\"}" + '}';
    }

}
