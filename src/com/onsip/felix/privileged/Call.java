package com.onsip.felix.privileged;

import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.logging.Level;

import com.onsip.felix.AppletLauncher;

/* Custom privilege with with support for argument phone number */
public class Call
    implements PrivilegedAction<Object>
{
    private final static java.util.logging.Logger m_logger = 
        java.util.logging.Logger.getLogger(Call.class.getName());
    
    private String sip;
    private String userId;
    
    public Call(String userId, String sip)
    {
        this.sip = sip;
        this.userId = userId;
    }
   
    @Override
    public String run()
    {
        try
        {                        
            Object service = AppletLauncher.getService();
                        
            Method call = getMethod(service); 
            call.invoke(service, new Object[] {this.userId, this.sip});
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
        Class<? extends Object> clazz = service.getClass();
        
        Method m = clazz.getMethod("call", 
            new Class[] {String.class, String.class});
        return m;
    }
    
    public static Method getMethodWithSetupId(Object service) 
        throws SecurityException, NoSuchMethodException        
    {                
        Class<? extends Object> clazz = service.getClass();
    
        Method m = clazz.getMethod("call", 
            new Class[] {String.class, String.class, String.class});
        return m;
    }
}