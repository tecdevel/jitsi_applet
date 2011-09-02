package com.onsip.felix;

/*
 * Junction Networks 2011
 */

import java.applet.Applet;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.util.*;
import java.util.logging.Level;

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

import com.onsip.felix.exceptions.UnsupportedPlatformException;
import com.onsip.felix.handlers.CallHandler;
import com.onsip.felix.handlers.CallPeerHandler;
import com.onsip.felix.handlers.LoadHandler;
import com.onsip.felix.handlers.RegistrationHandler;
import com.onsip.felix.privileged.*;
import com.onsip.felix.sources.CallEventSource;
import com.onsip.felix.sources.CallPeerEventSource;
import com.onsip.felix.sources.LoadEventSource;
import com.onsip.felix.sources.RegistrationEventSource;
import com.onsip.felix.updates.Updater;

import netscape.javascript.*;

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
 
    private final static String JS_EVT_LOADING = "loading";
    private final static String JS_EVT_LOADED = "loaded";
    private final static String JS_EVT_UNLOADED = "unloaded";
    private final static String JS_EVT_DOWNLOAD = "download";
    
    private final static int PERC_CREATE_FRAMEWORK = 10;
    private final static int PERC_INIT_FRAMEWORK = 15;
    private final static int PERC_DOWNLOAD_FRAMEWORK = 20;
    private final static int PERC_START_FRAMEWORK = 25;
    private final static int PERC_SETUP_HANDLERS = 90;
    private final static int PERC_COMPLETE = 100;
        
    private final static int EXPECTED_REGISTERED = 37;
    private static int REGISTERED_COUNTER = 0;
    
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
    
    private static Framework m_fwk = null;
        
    public static String CONFIG_URL = "";
           
    private static JSObject win = null;
    
    private LoadEventSource m_loadEventSource = null;
    
    static 
    {                
        m_logger.setParent(java.util.logging.Logger.getLogger("com.onsip"));
        
        Properties props = new Properties();            
        props.setProperty(".level", java.util.logging.Level.INFO.toString());
        
        props.setProperty("handlers", "java.util.logging.ConsoleHandler");            
        props.setProperty("java.util.logging.ConsoleHandler.level", 
            java.util.logging.Level.FINE.toString());
        
        props.setProperty("com.onsip.level", 
            java.util.logging.Level.FINE.toString());
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
            System.out.println("Error initializing log properties: " + e.getMessage());
            e.printStackTrace(); 
        }            
             
        try
        {
            m_logger.info("Check for any old storage caches");
            Updater.trash();
        }
        catch (Exception e)
        {
            System.out.println(
                "Error while trying to delete all cache stores " + e);
            e.printStackTrace();
        }
    }
        
    public void init()
    {
        try
        {                        
            win = JSObject.getWindow(this);
                                    
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
            
            if (a2m != null){                
                @SuppressWarnings("rawtypes")
                Class[] c = new Class[] { boolean.class };
                Method m = this.getClass().getClassLoader().
                    getClass().getMethod("setCodebaseLookup", c);
                m.invoke(this.getClass().getClassLoader(), 
                    new Object[]{ false });                                
            }
            
            /* Monitor the progress of bundle downloads */ 
            setupDownloadMonitoring();
            
            initFramework(new String[] {});                        
        }
        catch (Exception e)
        {            
            m_logger.log(Level.SEVERE, e.getMessage(), e);            
            m_logger.log(Level.SEVERE, 
                "create Framework didn't complete successfully");
        }
    }
    
    public void stop()
    {                                 
        Runtime.getRuntime().exit(0);        
    }    
    
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
                            m_loadEventSource.fireEvent(
                                new String[] 
                                   { getSerializedDownloadEvent(e) });                            
                            m_logger.log(Level.FINE, "Download finished " + e);                            
                        }
        
                        @Override
                        public void progressStart(ProgressEvent e)
                        {                                    
                            m_loadEventSource.fireEvent(
                                new String[] 
                                   { getSerializedDownloadEvent(e) });
                            m_logger.log(Level.FINE, "Download started " + e);
                        }
        
                        @Override
                        public void progressUpdate(ProgressEvent e)
                        {
                            m_loadEventSource.fireEvent(
                                new String[] 
                                   { getSerializedDownloadEvent(e) });
                            m_logger.log(Level.FINE, "Download updated " + e);
                        }
                    });
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Error initializing Progress Monitor: " + e.getMessage());
            e.printStackTrace(); 
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
        String cb = this.getParameter("callback");                        
        if (cb != null && cb.length() > 0)
        {            
            System.setProperty("onsip.js.callback", cb);            
            JS_FN_EXPORTED = cb;            
        }        
    }
          
     
    public void initFramework(String[] args) throws Exception
    {           
        /* Set the location of the felix properties file */
        System
            .setProperty(CONFIG_PROPERTIES_PROP, CONFIG_PROPERTIES_FILE_VALUE);

        /* Set the javascript callback function */
        setJsCallbackFn();
        
        /* Check if this platform can run the applet */ 
        Capabilities.isPlatformSupported();
                
        /* Load system properties. */
        AppletLauncher.loadSystemProperties();

        /* Read configuration properties. */
        Properties configProps = AppletLauncher.loadConfigProperties();
                                                        
        /* Copy framework properties from the system properties. */
        AppletLauncher.copySystemProperties(configProps);
                
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

        try
        {                                
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
            
            /* 25% Complete */
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
        }
        catch (Exception ex)
        {
            m_logger.log(Level.SEVERE, "Could not create framework: ", ex);
            /*
             * Should an error be thrown while trying to create the felix
             * framework, we'll attempt to delete all remnant cache storage.
             * Felix seems to have problems recovering from half written
             * bundles.
             */
            m_logger.log(Level.SEVERE, 
                "As a precaution remove the felix & jitsi cache store");
            Updater.cleanFelixCacheDir();
            
            //System.exit(0);
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
    private static FrameworkFactory getFrameworkFactory() throws Exception
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
        throw new Exception("Could not find framework factory.");
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
        catch (MalformedURLException ex)
        {
            m_logger.log(Level.SEVERE, "Main: ", ex);
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
        catch (Exception ex)
        {
            m_logger.log(Level.SEVERE, "Error loading system " + 
                " properties from " + propURL, ex);            
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
            String name = (String) e.nextElement();
            System.setProperty(name,
                Util.substVars(props.getProperty(name), name, null, null));
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
    protected static Properties loadConfigProperties() throws Exception
    {      
        /* See if the property URL was specified as a property.*/
        URL propURL = null;
        String custom = System.getProperty(CONFIG_PROPERTIES_PROP);
        if (custom == null)
        {
            throw new Exception("No " + CONFIG_PROPERTIES_FILE_VALUE + " found.");      
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
        Properties props = new Properties();
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
            String name = (String) e.nextElement();
            props.setProperty(name,
                Util.substVars(props.getProperty(name), name, null, props));
        }

        String felixCacheDir = getFelixCacheDir();
        props.setProperty("org.osgi.framework.storage", felixCacheDir);
        props.setProperty("felix.cache.rootdir", felixCacheDir);              
        return props;
    }

    protected static void copySystemProperties(Properties configProps)
    {
        @SuppressWarnings("unchecked")
        Enumeration<String> e =
            (Enumeration<String>) System.getProperties().propertyNames();
        while (e.hasMoreElements())
        {
            String key = (String) e.nextElement();
            if (key.startsWith("felix.")
                || key.startsWith("org.osgi.framework."))
            {
                configProps.setProperty(key, System.getProperty(key));
            }
        }
    }

    public static Framework getFramework() 
    {
        return m_fwk;
    }
     
    public synchronized static Object getService()
    {
        BundleContext context = getFramework().getBundleContext();                   
        
        ServiceReference sref =
            context.getServiceReference
                ("com.onsip.communicator.impl.applet.AppletActivator");

        Object service = context.getService(sref);
                        
        return service;                
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
        RegistrationEventSource registrationSource = new RegistrationEventSource();
        registrationSource.addEventListener(new RegistrationHandler());
                                
        CallEventSource callEventSource = new CallEventSource();
        callEventSource.addEventListener(new CallHandler());
        
        CallPeerEventSource callPeerEventSource = new CallPeerEventSource();
        callPeerEventSource.addEventListener(new CallPeerHandler());
        
        Object service = getService();            
        Class<? extends Object> clazz = service.getClass();
        
        try
        {     
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
           m_logger.log(Level.SEVERE, "SecurityException: " + e, e);
        }
        catch (NoSuchMethodException e)
        {
            m_logger.log(Level.SEVERE, "SecurityException: " + e, e);
        }                    
        catch (IllegalArgumentException e)
        {
            m_logger.log(Level.SEVERE, "IllegalArgumentException: " + e, e);
        }
        catch (IllegalAccessException e)
        {
            m_logger.log(Level.SEVERE, "IllegalAccessException: " + e, e);
        }
        catch (InvocationTargetException e)
        {
            m_logger.log(Level.SEVERE, "InvocationTargetException: " + e, e);
        }
    }
       
    /**
     * Currently the only exported OnSIP function
     * 
     * @param fnName
     * @param args
     * @return
     */
    public boolean api(String fnName, String [] args)
    {
        m_logger.log(Level.FINE, "In api call " + fnName + " " + args.length);
        for (int i=0; i < args.length ; i++)
        {
            if (!fnName.equalsIgnoreCase("register"))
            {
                m_logger.log(Level.FINE, "arg " + i + " => " + args[i]);
            }
        }
        AccessController.doPrivileged(new Generic(fnName, args));
        return true; 
    }
                       
    public void serviceChanged(ServiceEvent event)
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
    
    @Override
    public void bundleChanged(BundleEvent bundle)
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
   
    /**
     * 
     * @param state unloaded, loading, loaded
     * @param progress progress percentage
     * 
     * @return serialized json object
     */
    public String getSerializedEvent(String state, String msg, double progress)
    {                
        return '{' + "\"package\":\"loader\",\"type\":\"" + 
            state + "\",\"details\":{\"progress\":\"" + 
                (int) Math.abs(progress) + "\",\"message\":\"" + 
                    msg + "\"}" + '}';
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
            state = "complete";
        }
        else if (e.getState() == ProgressSource.State.CONNECTED)
        {
            state = "connected";
        }
        double progress = ((double) e.getProgress() / 
            (double) e.getExpected()) * 100;
        return '{' + "\"package\":\"loader\",\"type\":\"" + 
            JS_EVT_DOWNLOAD + "\",\"details\":{\"progress\":\"" + 
                ((int) Math.floor(progress)) + "\",\"url\":\"" +  
                    e.getURL().toExternalForm() + 
                        "\", \"message\":\"" +                
                            state + "\"}" + '}';        
    }
        
}
