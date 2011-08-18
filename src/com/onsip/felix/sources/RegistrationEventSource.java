package com.onsip.felix.sources;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;


import com.onsip.felix.listeners.RegistrationListener;


/**
 * RegistrationEventSource gets passed through Felix right into the Jitsi application which is running
 * our Custom Wrapper {@linkOnSipActivator}.  All our Wrapper needs to do is call <i>fireEvent</i> whenever
 * it deems an event is worthwhile enough to pass through to the UI.
 * 
 * This class, RegistrationEventSource, will be responsible for - possibly - translating the event
 * into some event object (i.e. RegirationEvent) and sending those events to listeners.
 * 
 * Our Registered Listeners (e.g. RegistrationStateChangeListener, will be setup in AppletLauncher if
 * there is an associated Javascript function), and will be responsible for delivering callbacks down to the DOM)
 * @author oren
 *
 */
public class RegistrationEventSource
{    
    private List<RegistrationListener> _listeners = 
        new ArrayList<RegistrationListener>();
    
    public synchronized void addEventListener
        (RegistrationListener listener)  
    {
        _listeners.add(listener);
    }
    
    public synchronized void removeEventListener
        (RegistrationListener listener)   
    {
        _listeners.remove(listener);
    }
    
    /* This function will be called from inside the Jitsi application wrapper */
    public synchronized void fireEvent(String args[]) 
    {
        Iterator<RegistrationListener> i = _listeners.iterator();
        while(i.hasNext())
        {   
            RegistrationListener rsl = 
                (RegistrationListener) i.next();
            rsl.handleEvent(args);
        }        
    }
}
