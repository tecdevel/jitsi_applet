package com.onsip.felix.privileged;

import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.logging.Level;

import com.onsip.felix.AppletLauncher;

public class Unregister
    implements PrivilegedAction<Object>
{
    private final static java.util.logging.Logger m_logger = 
        java.util.logging.Logger.getLogger(Unregister.class.getName());
    
    private String userId;
    
    public Unregister(String userId)
    {        
        this.userId = userId;
    }
    
    @Override
    public String run()
    {
        try
        {            
            Object service = AppletLauncher.getService();
                                                                                                    
            Method m = getMethod(service);
            m.invoke(service, new Object[] {this.userId});                    
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
                        
        Method m = clazz.getMethod("unregister", new Class[] {String.class});
                
        return m;
    }
}
