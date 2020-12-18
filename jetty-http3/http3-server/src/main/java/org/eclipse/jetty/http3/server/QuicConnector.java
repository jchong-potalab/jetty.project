package org.eclipse.jetty.http3.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.EventListener;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.io.SelectorManager;
import org.eclipse.jetty.server.AbstractNetworkConnector;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.thread.Scheduler;

public class QuicConnector extends AbstractNetworkConnector
{
    private final SelectorManager _manager;
    private final AtomicReference<Closeable> _acceptor = new AtomicReference<>();
    private volatile DatagramChannel _acceptChannel;
    private volatile int _localPort = -1;
    private volatile boolean _reuseAddress = true;

    public QuicConnector(
        @Name("server") Server server)
    {
        this(server, null, null, null, -1, -1, new HttpConnectionFactory());
    }

    public QuicConnector(
        @Name("server") Server server,
        @Name("executor") Executor executor,
        @Name("scheduler") Scheduler scheduler,
        @Name("bufferPool") ByteBufferPool bufferPool,
        @Name("acceptors") int acceptors,
        @Name("selectors") int selectors,
        @Name("factories") ConnectionFactory... factories
    )
    {
        super(server, executor, scheduler, bufferPool, acceptors, factories);
        _manager = newSelectorManager(getExecutor(), getScheduler(), selectors);
        addBean(_manager, true);
    }

    private SelectorManager newSelectorManager(Executor executor, Scheduler scheduler, int selectors)
    {
        return new QuicConnectorManager(executor, scheduler, selectors);
    }

    @Override
    public Object getTransport()
    {
        return _acceptChannel;
    }

    @Override
    protected void doStart() throws Exception
    {
        for (EventListener l : getBeans(SelectorManager.SelectorManagerListener.class))
            _manager.addEventListener(l);

        super.doStart();

        if (getAcceptors() == 0)
        {
            _acceptChannel.configureBlocking(false);
            _acceptor.set(_manager.acceptor(_acceptChannel));
        }
    }

    @Override
    protected void doStop() throws Exception
    {
        super.doStop();
        for (EventListener l : getBeans(EventListener.class))
        {
            _manager.removeEventListener(l);
        }
    }

    @Override
    public boolean isOpen()
    {
        DatagramChannel channel = _acceptChannel;
        return channel != null && channel.isOpen();
    }

    @Override
    public void open() throws IOException
    {
        if (_acceptChannel == null)
        {
            _acceptChannel = openAcceptChannel();
            _acceptChannel.configureBlocking(true);
            _localPort = _acceptChannel.socket().getLocalPort();
            if (_localPort <= 0)
                throw new IOException("Server channel not bound");
            addBean(_acceptChannel);
        }
    }

    protected DatagramChannel openAcceptChannel() throws IOException
    {
        InetSocketAddress bindAddress = getHost() == null ? new InetSocketAddress(getPort()) : new InetSocketAddress(getHost(), getPort());
        DatagramChannel channel = DatagramChannel.open();
        try
        {
            channel.socket().setReuseAddress(_reuseAddress);
            channel.socket().bind(bindAddress);
            channel.configureBlocking(false);
        }
        catch (Throwable e)
        {
            IO.close(channel);
            throw new IOException("Failed to bind to " + bindAddress, e);
        }
        return channel;
    }

    @Override
    public void close()
    {
        super.close();

        DatagramChannel serverChannel = _acceptChannel;
        _acceptChannel = null;
        if (serverChannel != null)
        {
            removeBean(serverChannel);

            if (serverChannel.isOpen())
            {
                try
                {
                    serverChannel.close();
                }
                catch (IOException e)
                {
                    LOG.warn("Unable to close {}", serverChannel, e);
                }
            }
        }
        _localPort = -2;
    }

    @Override
    protected void accept(int acceptorID) throws IOException
    {
        DatagramChannel channel = _acceptChannel;
        if (channel != null && channel.isOpen())
        {
            //TODO implement QUIC accept
            accepted(channel);
        }
    }

    private void accepted(DatagramChannel channel) throws IOException
    {
        _manager.accept(channel);
    }

    QuicEndPoint newEndPoint(DatagramChannel channel, ManagedSelector selectSet, SelectionKey key) throws IOException
    {
        return new QuicEndPoint(channel, selectSet, key, getScheduler());
    }

    /**
     * @return whether the server socket reuses addresses
     * @see ServerSocket#getReuseAddress()
     */
    @ManagedAttribute("Server Socket SO_REUSEADDR")
    public boolean getReuseAddress()
    {
        return _reuseAddress;
    }

    @Override
    @ManagedAttribute("local port")
    public int getLocalPort()
    {
        return _localPort;
    }

    protected class QuicConnectorManager extends SelectorManager
    {
        protected QuicConnectorManager(Executor executor, Scheduler scheduler, int selectors)
        {
            super(executor, scheduler, selectors);
        }

        @Override
        protected void accepted(SelectableChannel channel) throws IOException
        {
            QuicConnector.this.accepted((DatagramChannel)channel);
        }

        @Override
        protected QuicEndPoint newEndPoint(SelectableChannel channel, ManagedSelector selector, SelectionKey selectionKey) throws IOException
        {
            return QuicConnector.this.newEndPoint((DatagramChannel)channel, selector, selectionKey);
        }

        @Override
        public Connection newConnection(SelectableChannel channel, EndPoint endpoint, Object attachment) throws IOException
        {
            return getDefaultConnectionFactory().newConnection(QuicConnector.this, endpoint);
        }

        @Override
        protected void endPointOpened(EndPoint endpoint)
        {
            super.endPointOpened(endpoint);
            onEndPointOpened(endpoint);
        }

        @Override
        protected void endPointClosed(EndPoint endpoint)
        {
            onEndPointClosed(endpoint);
            super.endPointClosed(endpoint);
        }

        @Override
        public String toString()
        {
            return String.format("SelectorManager@%s", QuicConnector.this);
        }
    }

}
