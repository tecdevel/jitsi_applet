package com.onsip.felix.privileged;

import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.onsip.felix.AppletLauncher;

public class Transfer
    implements PrivilegedAction<Object>
{
    private final static Logger m_logger = 
        Logger.getLogger(Transfer.class.getName());
    
    private String callId;
    private String peerId;
    private String targetUri;

    public Transfer(String callId, String peerId, String targetUri)
    {
        this.callId = callId;
        this.peerId = peerId;
        this.targetUri = targetUri;
    }
   
    @Override
    public String run()
    {
        try
        {                        
            Object service = AppletLauncher.getService();
                        
            Method m = getMethod(service);            
            m.invoke(service, 
                new Object[] {this.callId, this.peerId, this.targetUri});
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
        
        Method m = clazz.getMethod("transfer", 
            new Class[]{String.class, String.class, String.class});
                
        return m;
    }
        
}


