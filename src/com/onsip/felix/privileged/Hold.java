package com.onsip.felix.privileged;

import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.onsip.felix.AppletLauncher;

public class Hold
    implements PrivilegedAction<Object>
{
    private final static Logger m_logger = 
        Logger.getLogger(Mute.class.getName());
    
    private boolean hold;

    public Hold(boolean hold)
    {
        this.hold = hold;
    }
   
    @Override
    public String run()
    {
        try
        {                        
            Object service = AppletLauncher.getService();
                        
            Method m = getMethod(service);            
            m.invoke(service, new Object[] {this.hold});
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
        
        Method m = clazz.getMethod("hold", new Class[] {Boolean.class});   
                
        return m;
    }
    
    public static Method getMethodForCallId(Object service) 
        throws SecurityException, NoSuchMethodException        
    {                
        Class<? extends Object> clazz =
            service.getClass();
    
        Method m = clazz.getMethod("hold", 
            new Class[]{String.class, Boolean.class});
        
        return m;
    }
    
    public static Method getMethodForCallIdAndPeerId(Object service) 
        throws SecurityException, NoSuchMethodException        
    {                
        Class<? extends Object> clazz =
            service.getClass();
    
        Method m = clazz.getMethod("hold", 
            new Class[]{String.class, String.class, Boolean.class});
        
        return m;
    }
}

