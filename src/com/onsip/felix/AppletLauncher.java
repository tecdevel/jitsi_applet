package com.onsip.felix;

/*
 * Junction Networks 2011
 */

import java.applet.Applet;
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

import com.onsip.felix.handlers.CallHandler;
import com.onsip.felix.handlers.CallPeerHandler;
import com.onsip.felix.handlers.LoadHandler;
import com.onsip.felix.handlers.RegistrationHandler;
import com.onsip.felix.privileged.*;
import com.onsip.felix.sources.CallEventSource;
import com.onsip.felix.sources.CallPeerEventSource;
import com.onsip.felix.sources.LoadEventSource;
import com.onsip.felix.sources.RegistrationEventSource;

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
            
            m_logger.log(Level.INFO, "");            
            m_logger.log(Level.INFO, "OS name: " + 
                System.getProperty("os.name"));
            m_logger.log(Level.INFO, "OS architecture: " + 
                System.getProperty("os.arch"));
            m_logger.log(Level.INFO, "OS version: " + 
                System.getProperty("os.version"));
            m_logger.log(Level.INFO, "tmpdir: " + 
                System.getProperty("java.io.tmpdir"));
            m_logger.log(Level.INFO, "deployment cache dir: " + 
                System.getProperty("deployment.user.cachedir"));
            m_logger.log(Level.INFO, "system cache dir: " + 
                System.getProperty("deployment.system.cachedir"));
                        
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
                                    
            main(new String[] {});           
        }
        catch (Exception e)
        {            
            m_logger.log(Level.SEVERE, e.getMessage(), e);            
            m_logger.log(Level.SEVERE, 
                "create Framework didn't complete successfully");
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

    private static void cleanFelixCacheDir() 
    {
        m_logger.log(Level.FINE, "Clean up felix / jitsi storage");
        String dir = System.getProperty("deployment.user.cachedir");
        if (dir == null) {
            dir = System.getProperty("user.home");
        }
        String separator = System.getProperty("file.separator");
        m_logger.log(Level.FINE, "Using felix cache dir - " + dir);
        File fFelixCache = new File(dir + separator + "felix-cache");
        
        /* first, we try to delete the felix cache */
        try
        {
            if (fFelixCache.exists() && fFelixCache.isDirectory()) 
            {
                fFelixCache.delete();
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Error while deleting felix cache directory, " + 
                "try deleting on exit");
            try
            {
                /* if deletion fails, then try to remove on exit */
                if (fFelixCache.exists() && fFelixCache.isDirectory()) 
                {
                    fFelixCache.deleteOnExit();
                }   
            }
            catch(Exception e2)
            {
                m_logger.log(Level.SEVERE, 
                    "Error even while deleting felix cache directory on exit");
            }
        }
        
        File fSipCommunicator = new File(dir + separator + ".sip-communicator");
        /* second, we try to delete the sip communicator cache */
        try
        {
            if (fSipCommunicator.exists() && fSipCommunicator.isDirectory()) 
            {
                fSipCommunicator.delete();
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Error while deleting jitsi cache directory, " + 
                "try deleting on exit");
            try
            {
                /* if deletion fails, then try to remove on exit */
                if (fSipCommunicator.exists() && fSipCommunicator.isDirectory()) 
                {
                    fSipCommunicator.deleteOnExit();
                }   
            }
            catch(Exception e2)
            {
                m_logger.log(Level.SEVERE, 
                    "Error even while deleting jitsi cache directory on exit");
            }
        }
    }
    
    private void setPropertyOS() throws OSNotSupportedException
    {
        String os = System.getProperty("os.name");
        os = os.toLowerCase();
        if (os.indexOf("mac os x") != -1)
        {
            /* mac */
            System.setProperty("onsip.os", "macosx");
        }
        else if (os.indexOf("windows") != -1)
        {
            /* windows */
            System.setProperty("onsip.os", "windows");
        }
        else if (os.indexOf("linux") != -1)
        {
            /* linux */
            System.setProperty("onsip.os", "linux");
        }
        else if (os.indexOf("sunos") != -1)
        {
            /* solaris */
            System.setProperty("onsip.os", "solaris");
        }
        else if (os.indexOf("freebsd") != -1)
        {
            /* FreeBSD */
            System.setProperty("onsip.os", "freebsd");
        }
        else
        {
            throw new OSNotSupportedException("OS " + os
                + " not currently supported");
        }
    }
    
    // throw JS callback not set exception
    private void setJsCallbackFn()
    {        
        String cb = this.getParameter("callback");                
        System.out.println();
        System.out.println("Tried searching for parameter call back " + cb);
        System.out.println();
        if (cb != null)
        {
            if (cb.length() > 0)
            {
                System.setProperty("onsip.js.callback", cb);  
                JS_FN_EXPORTED = cb;
            }
        }        
    }
    
    public void main(String[] args) throws Exception
    {           
        /* Set the location of the felix properties file */
        System
            .setProperty(CONFIG_PROPERTIES_PROP, CONFIG_PROPERTIES_FILE_VALUE);

        /* Set the javascript callback function */
        setJsCallbackFn();
        
        /* Set OS property, used in the OSGI properties file */ 
        setPropertyOS();

        /* Load system properties. */
        AppletLauncher.loadSystemProperties();

        /* Read configuration properties. */
        Properties configProps = AppletLauncher.loadConfigProperties();
                                                        
        /* Copy framework properties from the system properties. */
        AppletLauncher.copySystemProperties(configProps);

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
                        (JS_EVT_LOADING, "create new framework", 10) });
                        
            /* Create an instance of the framework. */
            FrameworkFactory factory = getFrameworkFactory();
            m_fwk = factory.newFramework(configProps);

            /* 15% Complete */
            m_loadEventSource.fireEvent(
                new String[] 
                   { this.getSerializedEvent
                        (JS_EVT_LOADING, "init framework", 15) });
                        
            /* Initialize the framework, but don't start it yet. */            
            m_fwk.init();            
            m_fwk.getBundleContext().addBundleListener(this);

            /* 20% Complete */
            m_loadEventSource.fireEvent(
                new String[] 
                   { this.getSerializedEvent
                        (JS_EVT_LOADING, "download framework bundles", 20) });
            
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
                        (JS_EVT_LOADING, "start framework bundles", 25) });
                        
            /* Start the framework. */            
            m_fwk.start();             
              
            /* 90% Complete */
            m_loadEventSource.fireEvent(
                new String[] 
                   { this.getSerializedEvent
                        (JS_EVT_LOADED, 
                            "setup event handlers in activator", 90) });
            
            initHandlers();                        
            
            /* 100% Complete */
            m_loadEventSource.fireEvent(
                new String[] 
                   { this.getSerializedEvent(JS_EVT_LOADED, "done", 100) });
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
            cleanFelixCacheDir();
            /* 
             * We can bail out of the virtual machine completely,
             * but that's just kind of fugly from an end user experience.
             * What we want to do is generate an error and pass it down
             * to our javascript lib. 
             */
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
        System.out.println("In api call " + fnName + " " + args.length);
        for (int i=0; i < args.length ; i++)
        {
            System.out.println("arg " + i + " => " + args[0]);
        }
        AccessController.doPrivileged(new Generic(fnName, args));
        return true; 
    }
        
    public long status() 
    {
        return new Date().getTime();
    }
    
    public void stop()
    {                         
        Runtime.getRuntime().exit(0);        
    }    
    
    public void destroy()
    {        
        System.exit(0);
    }
        
    public void serviceChanged(ServiceEvent event)
    {
        
        String[] objectClass =
            (String[]) event.getServiceReference().getProperty("objectClass");

        if (event.getType() == ServiceEvent.REGISTERED)
        {            
            m_logger.log(Level.FINE, "Service of type "
                + objectClass[0] + " REGISTERED. ");                                                                                                            
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
    
    /**
     * We temporarily remove these 
     * exported functions in favor of a single
     * API function that parses the request
     */
    
    /**
     * @return microphone info
     */
    /**
    public String micCheck()
    {
        String mic = (String) AccessController.doPrivileged
            (new DefaultAudioDevice());
        return mic;
    }
    **/
    
    /**
     * Answer call
     */
    /**
    public void pickupCall()
    {
        AccessController.doPrivileged(new PickupCall());
    }
    **/
    
    /**
     * Terminate call
     */
    /**
    public void hangup()
    {
        AccessController.doPrivileged(new Terminate());
    }
    **/
    
    /**
     * Make outgoing call
     * @param sip DID
     */
    /**
    public void makeCall(String sip)
    {
        AccessController.doPrivileged(new Call(sip));
    }
    **/
    
    /**
     * Mute / Un-Mute
     * @param mute
     */
    /**
    public void mute(String mute)
    {
        Boolean b = new Boolean(mute);
        AccessController.doPrivileged(new Mute(b.booleanValue()));
    }
    **/
    
    /**
     * Register device
     * 
     * @param userId
     * @param displayName
     * @param authUsername 
     * @param password
     */
    /**
    public void register(String userId, String displayName,
        String authUsername, String password)
    {
        AccessController.doPrivileged(
            new Register(userId, displayName, authUsername, password));
    }
    **/
    
    /**
     * Unregister device
     */
    /**
    public void unregister()
    {
        AccessController.doPrivileged(new Unregister());
    }
    **/
    
}
