package com.onsip.felix.privileged;

import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.onsip.felix.AppletLauncher;

public class PlaybackVolume
    implements PrivilegedAction<Object>
{
    private final static Logger m_logger =
        Logger.getLogger(PlaybackVolume.class.getName());

    private Integer level;

    public PlaybackVolume(Integer level)
    {
        this.level = level;
    }

    @Override
    public String run()
    {
        try
        {
            Object service = AppletLauncher.getService();

            Method m = getMethod(service);
            m.invoke(service, new Object[] {this.level});
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }

    public static Method getMethod(Object service)
        throws SecurityException, NoSuchMethodException
    {
        Class<? extends Object> clazz =
            service.getClass();

        Method m = clazz.getMethod("setOutputVolume",
            new Class[] {Integer.class});

        return m;
    }

    public static Method getMethodWithNotify(Object service)
        throws SecurityException, NoSuchMethodException
    {
        Class<? extends Object> clazz =
            service.getClass();

        Method m = clazz.getMethod("setOutputVolume",
            new Class[] {Integer.class, Boolean.class});

        return m;
    }
}
