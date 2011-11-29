package com.onsip.felix;

import java.util.Calendar;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter
    extends Formatter
{
    private static final String MESSAGE_PATTERN = "[%2$s: %1$ta %1$tb %1$td %1$tZ %1$tY - %1$TI:%1$TM:%1$TS:%1$TL, %3$s]:\n%4$s %5$s\n";

    @Override
    public String format(LogRecord record)
    {       
        //new Date(record.getMillis())
        return String.format(MESSAGE_PATTERN,
            getCurrentCalendar(record),
            record.getLevel(),            
            record.getLoggerName(),
            record.getMessage() == null ? "" : record.getMessage(),
            record.getThrown() == null ? "" : record.getThrown());
    }

    private static Calendar getCurrentCalendar(LogRecord record)
    {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(record.getMillis());
        return c;
    }
}
