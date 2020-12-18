package org.eclipse.jetty.http3.quic.quiche;

import com.sun.jna.IntegerType;
import com.sun.jna.Native;

public class ssize_t extends IntegerType
{
    public ssize_t()
    {
        this(0);
    }
    public ssize_t(long value)
    {
        super(Native.SIZE_T_SIZE, value, false);
    }
}
