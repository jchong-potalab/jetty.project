package org.eclipse.jetty.http3.quic.quiche;

import com.sun.jna.Native;
import com.sun.jna.ptr.ByReference;

public class size_t_pointer extends ByReference
{
    public size_t_pointer()
    {
        this((byte)0);
    }
    public size_t_pointer(long v)
    {
        super(Native.SIZE_T_SIZE);
        switch (Native.SIZE_T_SIZE)
        {
            case 4:
                getPointer().setInt(0, (int)v);
                break;
            case 8:
                getPointer().setLong(0, v);
                break;
            default:
                throw new AssertionError("Unsupported native size_t size: " + Native.SIZE_T_SIZE);
        }
    }

    public long getValue()
    {
        switch (Native.SIZE_T_SIZE)
        {
            case 4:
                return getPointer().getInt(0);
            case 8:
                return getPointer().getLong(0);
            default:
                throw new AssertionError("Unsupported native size_t size: " + Native.SIZE_T_SIZE);
        }
    }

    public size_t getPointee()
    {
        return new size_t(getValue());
    }
}
