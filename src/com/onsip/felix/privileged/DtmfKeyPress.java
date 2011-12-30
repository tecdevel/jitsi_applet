package com.onsip.felix.privileged;

import java.lang.reflect.Method;
import java.security.PrivilegedAction;

import java.util.logging.Logger;
import java.util.logging.Level;

import com.onsip.felix.AppletLauncher;

public class DtmfKeyPress 
    implements PrivilegedAction<Object>
{   
    private final static Logger m_logger = 
        Logger.getLogger(DtmfKeyPress.class.getName());
                
    char key;
        
    public DtmfKeyPress(String key)
    {            
        if (key.length() == 1)
        {
            this.key = key.charAt(0);            
        }
    }
    
    @Override
    public String run()
    {
        try
        {               
            Object service = AppletLauncher.getService();
                        
            Method m = getMethod(service);
            
            m.invoke(service, 
                new Object[] { this.key });
        }            
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }    
    
    public static Method getMethod(Object service) 
        throws SecurityException, NoSuchMethodException        
    {                
        Class<? extends Object> clazz =
            service.getClass();
        
        Method m = clazz.getMethod("dispatchKeyEvent", 
            new Class[] { char.class });
                                         
        return m;
    }
    
    public static Method getMethodForCallId(Object service) 
        throws SecurityException, NoSuchMethodException        
    {                
        Class<? extends Object> clazz =
            service.getClass();
    
        Method m = clazz.getMethod("dispatchKeyEvent", 
            new Class[]{String.class, char.class});
        
        return m;
    }
    
    public static Method getMethodForStartStop(Object service) 
        throws SecurityException, NoSuchMethodException        
    {                
        Class<? extends Object> clazz =
            service.getClass();
    
        Method m = clazz.getMethod("dispatchKeyEvent", 
            new Class[]{String.class, char.class, Boolean.class});
        
        return m;
    }
}
