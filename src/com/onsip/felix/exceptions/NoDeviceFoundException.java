package com.onsip.felix.exceptions;

public class NoDeviceFoundException
    extends RuntimeException
{       
    private static final long serialVersionUID = 2297324516175279363L;
    private static final String error = "No Input Device Found";
    
    public NoDeviceFoundException()
    {
        super(error);
    }
    
    public NoDeviceFoundException(String s)
    {
        super(s);
    }
   
}
