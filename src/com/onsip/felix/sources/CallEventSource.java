package com.onsip.felix.sources;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.onsip.felix.listeners.CallListener;

public class CallEventSource
{    
    private List<CallListener> _listeners = 
        new ArrayList<CallListener>();
    
    public synchronized void addEventListener
        (CallListener listener)  
    {
        _listeners.add(listener);
    }
    
    public synchronized void removeEventListener
        (CallListener listener)   
    {
        _listeners.remove(listener);
    }
    
    /* This function will be called from inside the Jitsi application wrapper */
    public synchronized void fireEvent(String args[]) 
    {
        Iterator<CallListener> i = _listeners.iterator();
        while(i.hasNext())
        {   
            CallListener rsl = 
                (CallListener) i.next();
            rsl.handleEvent(args);
        }        
    }
}

