package org.eclipse.jetty.http3.quic.quiche;

import com.sun.jna.ptr.ByReference;

public class bool_pointer extends ByReference
{
    public bool_pointer()
    {
        this(0);
    }
    public bool_pointer(int v)
    {
        super(1);
        getPointer().setByte(0, (byte)v);
    }

    public boolean getValue()
    {
        return getPointer().getByte(0) != 0;
    }
}
