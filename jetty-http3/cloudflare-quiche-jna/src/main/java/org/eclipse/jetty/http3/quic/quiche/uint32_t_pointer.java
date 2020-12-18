package org.eclipse.jetty.http3.quic.quiche;

import com.sun.jna.ptr.ByReference;

public class uint32_t_pointer extends ByReference
{
    public uint32_t_pointer()
    {
        this(0);
    }
    public uint32_t_pointer(int v)
    {
        super(4);
        getPointer().setInt(0, v);
    }

    public int getValue()
    {
        return getPointer().getInt(0);
    }

    public uint32_t getPointee()
    {
        return new uint32_t(getValue());
    }
}
