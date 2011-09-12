package com.onsip.felix.privileged;

import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.logging.Level;

import com.onsip.felix.AppletLauncher;

public class Terminate implements PrivilegedAction<Object>
{
    private final static java.util.logging.Logger m_logger = 
        java.util.logging.Logger.getLogger(Terminate.class.getName());
        
    private String callId;
    
    public Terminate(String callId)
    {        
        this.callId = callId;
    }
    
    @Override
    public String run()
    {
        try
        {            
            Object service = AppletLauncher.getService();
            
            Method call = getMethodForCallId(service); 
            
            call.invoke(service, new Object[] {this.callId});
        }        
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }
            
    public static Method getMethodForCallId(Object service) 
        throws SecurityException, NoSuchMethodException        
    {                
        Class<? extends Object> clazz =
            service.getClass();
    
        Method m = clazz.getMethod("hangUp", new Class[]{String.class});
        
        return m;
    }
    
    public static Method getMethodForCallIdAndPeerId(Object service) 
        throws SecurityException, NoSuchMethodException        
    {                
        Class<? extends Object> clazz =
            service.getClass();
    
        Method m = clazz.getMethod("hangUp", 
            new Class[]{String.class, String.class});
        
        return m;
    }
}
