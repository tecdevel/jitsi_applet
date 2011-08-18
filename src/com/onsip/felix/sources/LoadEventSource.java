package com.onsip.felix.sources;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.onsip.felix.listeners.LoadListener;

public class LoadEventSource
{    
    private List<LoadListener> _listeners = 
        new ArrayList<LoadListener>();
    
    public synchronized void addEventListener
        (LoadListener listener)  
    {
        _listeners.add(listener);
    }
    
    public synchronized void removeEventListener
        (LoadListener listener)   
    {
        _listeners.remove(listener);
    }
    
    /* This function will be called from inside the Jitsi application wrapper */
    public synchronized void fireEvent(String args[]) 
    {
        Iterator<LoadListener> i = _listeners.iterator();
        while(i.hasNext())
        {   
            LoadListener rsl = 
                (LoadListener) i.next();
            rsl.handleEvent(args);
        }        
    }
}