package com.onsip.felix.privileged;

import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;

import netscape.javascript.JSObject;

import com.onsip.felix.AppletLauncher;
import com.onsip.felix.exceptions.NoSuchApiFunction;
import com.onsip.felix.exceptions.ParamsDoNotMatchException;

/** 
 * @author Junction Networks
 */
public class Generic
    implements PrivilegedAction<Object>
{
    private final static Logger m_logger = 
        Logger.getLogger(Generic.class.getName());
    
    public enum Exported_API
    {
        REGISTER, UNREGISTER, REREGISTER, 
        CALL_CREATE, CALL_TERMINATE, CALL_REQUESTED, INVITE, TRANSFER_CALL,
        SEND_TONE, MUTE, HOLD, DEFAULT_AUDIO_DEVICE, VOLUME; 
    }

    private String fn;
    private String[] args ;

    public Generic(String fnName, String [] args)
    {
        this.fn = fnName;        
        this.args = args;        
    }
   
    @Override
    public String run()
    {
        try
        {                        
            Object service = AppletLauncher.getService();
                        
            Method m = parseAPI(service);
            if (m == null)
            {
                throw new NoSuchApiFunction();
            }
            
            Class<?>[] params = m.getParameterTypes();
            int len = params.length;
            if (len != this.args.length)
            {
                throw new ParamsDoNotMatchException();
            }
            Object [] argos = new Object[len];            
            for (int i=0; i < len; i++)
            {                
                if (params[i].getName().equals("char"))
                {                                        
                    argos[i] = new Character(this.args[i].charAt(0)); 
                }
                else if (params[i].getName().equals("java.lang.Boolean"))
                {
                    argos[i] = new Boolean(this.args[i]);
                }
                else
                {
                    // default String
                    argos[i] = this.args[i];
                }                
            }                 
            Object returnType = m.invoke(service, argos);
            if (returnType != null && returnType.getClass() != Void.class)
            {
                m_logger.info("Return is " + returnType.toString());
                JSObject win = AppletLauncher.getJSObject();
                if (win != null)
                {                
                    String [] ret = new String[1];
                    ret[0] = returnType.toString();
                    win.call(AppletLauncher.JS_FN_EXPORTED, ret); 
                } 
            }
        }        
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }
        
    private Method parseAPI(Object service) 
        throws SecurityException, NoSuchMethodException
    {
        Method m = null;        
        switch (Exported_API.valueOf(fn))
        {
            case REGISTER:
                if (this.args.length == 4)
                {
                    m = Register.getMethod(service);
                }
                else if (this.args.length == 7)
                {
                    m = Register.getMethodWithServer(service);
                }
                else if (this.args.length == 0)
                {
                    m = Register.getMethodReRegister(service);
                }
                break;
            case UNREGISTER:
                m = Unregister.getMethod(service);
                break;
            case CALL_CREATE:                
                if (this.args.length == 2)
                {
                    m = Call.getMethod(service);
                }
                else
                {
                    m = Call.getMethodWithSetupId(service);
                }                
                break;
            case CALL_TERMINATE:
                if(this.args.length == 1)
                {
                    m = Terminate.getMethodForCallId(service);
                }   
                else
                {
                    m = Terminate.getMethodForCallIdAndPeerId(service);
                }                
                break;
            case CALL_REQUESTED:
                if (this.args.length == 0)
                {
                    m = PickupCall.getMethod(service);
                }
                else if(this.args.length == 1)
                {
                    m = PickupCall.getMethodForCallId(service);
                }                
                break;    
            case SEND_TONE:                
                if (this.args.length == 1)
                {
                    m = DtmfKeyPress.getMethod(service);
                }
                else if(this.args.length == 2)
                {
                    m = DtmfKeyPress.getMethodForCallId(service);
                }
                else if (this.args.length == 3)
                {                    
                    m = DtmfKeyPress.getMethodForStartStop(service);
                }
                break;
            case MUTE:
                if (this.args.length == 1)
                {
                    m = Mute.getMethod(service);
                }
                else if(this.args.length == 2)
                {
                    m = Mute.getMethodForCallId(service);                    
                }               
                break;              
            case HOLD:
                if (this.args.length == 1)
                {
                    m = Hold.getMethod(service);
                }
                else if (this.args.length == 2)
                {
                    m = Hold.getMethodForCallId(service);
                }
                else if (this.args.length == 3)
                {
                    m = Hold.getMethodForCallIdAndPeerId(service);
                }
                break;
            case TRANSFER_CALL:
                m = Transfer.getMethod(service);
                break;
            case INVITE:
                m = Invite.getMethod(service);
                break;
            case DEFAULT_AUDIO_DEVICE:
                m = DefaultAudioDevice.getMethod(service);
                break;                     
            case VOLUME:
                break;                        
        }
        
        return m;
    }
}
