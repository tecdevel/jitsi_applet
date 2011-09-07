package com.onsip.felix.privileged;

import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.logging.Level;

import com.onsip.felix.AppletLauncher;

/* Custom privilege with with support for argument phone number */
public class Invite
    implements PrivilegedAction<Object>
{
    private final static java.util.logging.Logger m_logger = 
        java.util.logging.Logger.getLogger(Invite.class.getName());
    
    private String callId;
    private String sipUri;
            
    public Invite(String callId, String sipUri)
    {
        this.callId = callId;
        this.sipUri = sipUri;        
    }
   
    @Override
    public String run()
    {
        try
        {                        
            Object service = AppletLauncher.getService();                        
            Method call = getMethod(service); 
            call.invoke(service, new Object[] {this.callId, this.sipUri});
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
        
        Method m = clazz.getMethod("inviteCalleeToCall", 
            new Class[] {String.class, String.class});
        return m;
    }
}