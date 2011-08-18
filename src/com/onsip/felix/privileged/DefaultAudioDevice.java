package com.onsip.felix.privileged;

import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.logging.Level;

import com.onsip.felix.AppletLauncher;

public class DefaultAudioDevice 
    implements PrivilegedAction<String>
{
    private final static java.util.logging.Logger m_logger = 
        java.util.logging.Logger.getLogger(DefaultAudioDevice.class.getName());
    
    @Override
    public String run()
    {
        String mic = null;
        try
        {            
            Object service = AppletLauncher.getService();
            
            Method m = getMethod(service);
            Object microphone = m.invoke(service, new Object[0]);
            if (microphone == null){
                return "";
            } else {
                mic = (String) microphone; 
            }
        }        
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return mic;
    }
    
    public static Method getMethod(Object service) 
        throws SecurityException, NoSuchMethodException        
    {                
        Class<? extends Object> clazz =
            service.getClass();
        
        Method m = clazz.getMethod("getDefaultAudioDevice", 
            new Class[0]);
                           
        return m;
    }
}
