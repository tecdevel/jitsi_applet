package com.onsip.felix.exceptions;

public class NoSuchApiFunction
    extends RuntimeException
{
      
    private static final long serialVersionUID = 5705045861328811554L;
    
    private static final String error = 
        "Could not match method to API function";
    
    public NoSuchApiFunction()
    {
        super(error);
    }
    
    public NoSuchApiFunction(String s)
    {
        super(s);
    }
    
}