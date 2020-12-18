package org.eclipse.jetty.http3.quic.quiche;

import com.sun.jna.IntegerType;

public class uint8_t extends IntegerType
{
    public uint8_t()
    {
        this((byte)0);
    }
    public uint8_t(byte v)
    {
        super(1, v, true);
    }
}
