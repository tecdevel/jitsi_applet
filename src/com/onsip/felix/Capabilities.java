package com.onsip.felix;

import java.util.logging.Level;

import com.onsip.felix.exceptions.UnsupportedPlatformException;

public class Capabilities
{
    private final static java.util.logging.Logger m_logger = 
        java.util.logging.Logger.getLogger(Capabilities.class.getName());
    
    private static String getOS() 
        throws UnsupportedPlatformException
    {
        String os = System.getProperty("os.name");                
        if (os.startsWith("Windows"))
        {
            return "win32";
        }
        else if (os.startsWith("Mac"))
        {
            return "mac";
        }
        /**
        else if (os.equals("SunOS"))
        {
            return "solaris";
        }
        else if (os.equals("Linux"))
        {
            return "linux";
        }
        **/
        else
        {
            throw new UnsupportedPlatformException(
                "Operating system " + os + " not yet supported");
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
            String os = getOS();
            String cpu = getCPU();
            boolean supported = false;
            
            if (cpu.equals("x86") || cpu.equals("amd64"))
            {
                String v = System.getProperty("os.version");
                if (os.equals("mac"))
                {
                    supported = ((v.compareTo("10.6") >= 0) && 
                        isJavaVersionSupported());                                    
                }
                else if(os.equals("win32"))
                {
                    supported = ((v.compareTo("5.1") >= 0) && 
                        isJavaVersionSupported());
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
