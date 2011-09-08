package com.onsip.felix.privileged;

import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.logging.Level;

import com.onsip.felix.AppletLauncher;

/* Custom privilege with with support for argument phone number */
public class Register
    implements PrivilegedAction<Object>
{
    private final static java.util.logging.Logger m_logger = 
        java.util.logging.Logger.getLogger(Register.class.getName());
    
    private String userId;
    private String displayName;
    private String authUsername;
    private String passwd;

    public Register(String userId, String displayName,
        String authUsername, String password)
    {
        this.userId = userId;
        this.displayName = displayName;
        this.authUsername = authUsername;
        this.passwd = password;
    }
    
    @Override
    public String run()
    {
        try
        {            
            Object service = AppletLauncher.getService();
                                             
            Method m = getMethod(service); 
                                                 
            m.invoke(service, new Object[]
                { userId, displayName, authUsername, passwd });                                                                                                     
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
        
        Method m = clazz.getMethod("register", 
            new Class[] 
                { String.class, String.class, String.class, String.class });
                    
        return m;
    }
    
    public static Method getMethodWithServer(Object service) 
        throws SecurityException, NoSuchMethodException        
    {                
        Class<? extends Object> clazz =
            service.getClass();
        
        Method m = clazz.getMethod("register", 
            new Class[] 
                { String.class, String.class, String.class, String.class,
                    String.class, String.class, String.class });
                    
        return m;
    }
}
