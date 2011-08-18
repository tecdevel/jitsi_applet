package com.onsip.felix.exceptions;

public class ParamsDoNotMatchException
    extends RuntimeException
{
      
    private static final long serialVersionUID = 5705045861328811553L;
    
    private static final String error = "Parameters don't match";
    
    public ParamsDoNotMatchException()
    {
        super(error);
    }
    
    public ParamsDoNotMatchException(String s)
    {
        super(s);
    }
    
}
