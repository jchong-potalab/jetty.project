package org.eclipse.jetty.http3.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

import org.eclipse.jetty.io.AbstractEndPoint;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.util.thread.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuicEndPoint extends AbstractEndPoint implements ManagedSelector.Selectable
{
    private static final Logger LOG = LoggerFactory.getLogger(QuicEndPoint.class);
    private final DatagramChannel _channel;
    private final ManagedSelector _selector;
    private final SelectionKey _key;

    protected QuicEndPoint(DatagramChannel channel, ManagedSelector selector, SelectionKey key, Scheduler scheduler)
    {
        super(scheduler);
        this._channel = channel;
        this._selector = selector;
        this._key = key;
    }

    @Override
    public InetSocketAddress getLocalAddress()
    {
        return (InetSocketAddress)_channel.socket().getLocalSocketAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress()
    {
        return (InetSocketAddress)_channel.socket().getRemoteSocketAddress();
    }

    @Override
    public boolean isOpen()
    {
        return _channel.isOpen();
    }

    @Override
    protected void doShutdownOutput()
    {
        //TODO shutdown QUIC connection
    }

    @Override
    public void doClose()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("doClose {}", this);
        try
        {
            _channel.close();
        }
        catch (IOException e)
        {
            LOG.debug("Unable to close channel", e);
        }
        finally
        {
            super.doClose();
        }
    }

    @Override
    public void onClose(Throwable cause)
    {
        try
        {
            super.onClose(cause);
        }
        finally
        {
            if (_selector != null)
                _selector.destroyEndPoint(this, cause);
        }
    }

    @Override
    public int fill(ByteBuffer buffer) throws IOException
    {
        return 0;
    }

    @Override
    public boolean flush(ByteBuffer... buffer) throws IOException
    {
        return false;
    }

    @Override
    public Object getTransport()
    {
        return _channel;
    }

    @Override
    public Runnable onSelected()
    {
        return null;
    }

    @Override
    public void updateKey()
    {

    }

    @Override
    public void replaceKey(SelectionKey newKey)
    {

    }

    @Override
    protected void onIncompleteFlush()
    {

    }

    @Override
    protected void needsFillInterest() throws IOException
    {

    }
}
