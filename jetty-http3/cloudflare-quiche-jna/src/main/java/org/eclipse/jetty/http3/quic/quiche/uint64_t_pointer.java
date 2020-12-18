package org.eclipse.jetty.http3.quic.quiche;

import com.sun.jna.ptr.ByReference;

public class uint64_t_pointer extends ByReference
{
    public uint64_t_pointer()
    {
        this(0);
    }
    public uint64_t_pointer(long v)
    {
        super(8);
        getPointer().setLong(0, v);
    }

    public long getValue()
    {
        return getPointer().getLong(0);
    }

    public uint64_t getPointee()
    {
        return new uint64_t(getValue());
    }
}
