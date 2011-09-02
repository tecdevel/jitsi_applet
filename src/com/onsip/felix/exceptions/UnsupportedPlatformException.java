package com.onsip.felix.exceptions;

public class UnsupportedPlatformException
    extends RuntimeException
{      
    private static final long serialVersionUID = 5705045861328811553L;
    
    private static final String error = "Parameters don't match";
    
    public UnsupportedPlatformException()
    {
        super(error);
    }
    
    public UnsupportedPlatformException(String s)
    {
        super(s);
    }
}
