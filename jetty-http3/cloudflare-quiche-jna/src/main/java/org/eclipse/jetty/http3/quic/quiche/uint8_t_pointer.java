package org.eclipse.jetty.http3.quic.quiche;

import com.sun.jna.ptr.ByReference;

public class uint8_t_pointer extends ByReference
{
    public uint8_t_pointer()
    {
        this((byte)0);
    }
    public uint8_t_pointer(byte v)
    {
        super(1);
        getPointer().setByte(0, v);
    }

    public byte getValue()
    {
        return getPointer().getByte(0);
    }

    public uint8_t getPointee()
    {
        return new uint8_t(getValue());
    }
}
