package com.onsip.felix;

import java.security.AccessController;
import java.util.logging.Level;

import com.onsip.felix.exceptions.NoDeviceFoundException;
import com.onsip.felix.exceptions.UnsupportedPlatformException;
import com.onsip.felix.privileged.DefaultAudioDevice;

public class Capabilities
{
    private final static java.util.logging.Logger m_logger = 
        java.util.logging.Logger.getLogger(Capabilities.class.getName());
    
    private static String microphone = null;
    public synchronized static String getDefaultMicrophone()
    {
        microphone =  AccessController.doPrivileged(new DefaultAudioDevice());
        if (microphone == null && microphone.length() == 0)
        {
            throw new NoDeviceFoundException("No microphone found");
        }
        m_logger.info("Audio input device --> " + microphone);
        return microphone;
    }
    
    private synchronized static void setPropertyOS()
        throws UnsupportedPlatformException
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
            throw new UnsupportedPlatformException("OS " + os
                + " not currently supported");
        }
    }
           
    /* Returns "sparc" if on SPARC, "x86" if on x86. */
    private static String getCPU() 
        throws UnsupportedPlatformException
    {
        String cpu = System.getProperty("os.arch");        
        if (cpu.equals("i386") || cpu.equals("x86"))
        {
            return "x86";
        }                
        else if (cpu.equals("x86_64") || cpu.equals("amd64"))
        {
            return "amd64";
        }
        /**
        else if (cpu.equals("sparc") || cpu.equals("x86") || cpu.equals("ia64"))
        {
            return cpu;
        }                
        else if (cpu.equals("sparcv9"))
        {
            return "sparc";
        }
        **/
        else
        {
            throw new UnsupportedPlatformException(
                "CPU type " + cpu + " not yet supported");
        }
    }

    private static boolean isJavaVersionSupported()
    {
        String v = System.getProperty("java.version");
        if (v.compareTo("1.6.0_0") >= 0)
        {
            return true;
        }
        throw new UnsupportedPlatformException("Java version " + v
            + " not supported");
    }
    
    public static boolean isPlatformSupported()
    {
        try
        {
            setPropertyOS();
            String os = System.getProperty("onsip.os");
            String cpu = getCPU();
            boolean supported = false;
            
            if (cpu.equals("x86") || cpu.equals("amd64"))
            {
                String v = System.getProperty("os.version");
                if (os.equals("macosx"))
                {
                    supported = ((v.compareTo("10.5") >= 0) && 
                        isJavaVersionSupported());                                    
                }
                else if(os.equals("windows"))
                {
                    supported = ((v.compareTo("5.1") >= 0) && 
                        isJavaVersionSupported());
                }
                else if(os.equals("linux"))
                {
                    supported = isJavaVersionSupported();
                }
            }
            if (!supported)
            {
                throw new UnsupportedPlatformException
                    ("The applet is not supported on this platform");
            }
            return supported;
        }
        catch(UnsupportedPlatformException uspe)
        {
            m_logger.log(Level.SEVERE, 
                "Platform is not supported, details", uspe);
           throw uspe;
        }
    }        
}
