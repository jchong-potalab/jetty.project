package org.eclipse.jetty.http3.quic.quiche;

import com.sun.jna.IntegerType;

public class uint32_t extends IntegerType
{
    public uint32_t()
    {
        this(0);
    }
    public uint32_t(int v)
    {
        super(4, v, true);
    }
}
