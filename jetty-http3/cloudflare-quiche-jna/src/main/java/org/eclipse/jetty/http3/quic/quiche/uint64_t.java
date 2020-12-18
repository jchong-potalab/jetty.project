package org.eclipse.jetty.http3.quic.quiche;

import com.sun.jna.IntegerType;

public class uint64_t extends IntegerType
{
    public uint64_t()
    {
        this(0);
    }
    public uint64_t(long v)
    {
        super(8, v, true);
    }
}
