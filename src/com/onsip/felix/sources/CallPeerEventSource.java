package com.onsip.felix.sources;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.onsip.felix.listeners.CallListener;
import com.onsip.felix.listeners.CallPeerListener;

public class CallPeerEventSource
{    
    private List<CallPeerListener> _listeners = 
        new ArrayList<CallPeerListener>();
    
    public synchronized void addEventListener
        (CallPeerListener listener)  
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
        Iterator<CallPeerListener> i = _listeners.iterator();
        while(i.hasNext())
        {   
            CallPeerListener rsl = 
                (CallPeerListener) i.next();
            rsl.handleEvent(args);
        }        
    }
}