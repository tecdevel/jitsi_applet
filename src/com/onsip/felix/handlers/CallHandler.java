package com.onsip.felix.handlers;

import java.util.logging.Logger;
import java.util.logging.Level;

import com.onsip.felix.AppletLauncher;
import com.onsip.felix.listeners.CallListener;

import netscape.javascript.*;

public class CallHandler
    implements CallListener
{
    private final static Logger m_logger = 
        Logger.getLogger(CallHandler.class.getName());
            
    @Override
    public void handleEvent(String[] args)
    {        
        try
        {    
            JSObject win = AppletLauncher.getJSObject();
            if (win != null)
            {    
                /**
                String log = "CallHandler with "+ args.length + " arguments ";
                for (int i=0; i < args.length; i++)
                {                    
                    log += "( " + args[i] + " ) ";                    
                }                
                m_logger.info(log);
                **/                
                win.call(AppletLauncher.JS_FN_EXPORTED, args); 
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Error " + e.getMessage(), e);                
        }        
    }
}