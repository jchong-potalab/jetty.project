//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.http3.quic.quiche;

import com.sun.jna.ptr.ByReference;

public class bool_pointer extends ByReference
{
    public bool_pointer()
    {
        this(false);
    }
    public bool_pointer(boolean v)
    {
        super(1);
        getPointer().setByte(0, (byte)(v ? 1 : 0));
    }

    public boolean getValue()
    {
        return getPointer().getByte(0) != 0;
    }
}
