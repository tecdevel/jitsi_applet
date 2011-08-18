package com.onsip.felix;

public class OSNotSupportedException
    extends Exception
{    
    /**
     * Required serialization field
     */
    private static final long serialVersionUID = -4791101427151507383L;

    public OSNotSupportedException(String message) 
    {
        super(message);
    }
}
