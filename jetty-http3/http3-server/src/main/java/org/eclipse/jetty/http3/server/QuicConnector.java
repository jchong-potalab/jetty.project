package org.eclipse.jetty.http3.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.EventListener;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.http3.quic.QuicRawConnection;
import org.eclipse.jetty.http3.quic.QuicStream;
import org.eclipse.jetty.io.AbstractEndPoint;
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

public class QuicConnector extends AbstractNetworkConnector implements ManagedSelector.Selectable
{
    private final SelectorManager _manager;
    private final AtomicReference<Closeable> _acceptor = new AtomicReference<>();
    private volatile DatagramChannel _acceptChannel;
    private volatile int _localPort = -1;
    private volatile boolean _reuseAddress = true;

    public QuicConnector(
        @Name("server") Server server)
    {
        this(server, null, null, null, -1, new HttpConnectionFactory());
    }

    public QuicConnector(
        @Name("server") Server server,
        @Name("executor") Executor executor,
        @Name("scheduler") Scheduler scheduler,
        @Name("bufferPool") ByteBufferPool bufferPool,
        @Name("selectors") int selectors,
        @Name("factories") ConnectionFactory... factories
    )
    {
        super(server, executor, scheduler, bufferPool, 0, factories);
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

        _acceptChannel.configureBlocking(false);
        _manager.accept(_acceptChannel, this);
    }

    final ConcurrentMap<QuicRawConnection.QuicConnectionId, AbstractEndPoint> _endpoints = new ConcurrentHashMap<>();
    final ConcurrentMap<QuicRawConnection.QuicConnectionId, QuicRawConnection> _connections = new ConcurrentHashMap<>();

    final ConcurrentMap<Long, AbstractEndPoint> _endpoints2 = new ConcurrentHashMap<>();

    @Override
    public Runnable onSelected()
    {
        //TODO alternate between reading and writing

        ByteBuffer packetRead = getByteBufferPool().acquire(1500, true); //TODO replace 1500 with MTU
        try
        {
            while (true)
            {
                SocketAddress peer = _acceptChannel.receive(packetRead);
                if (peer == null)
                    break;

                while (packetRead.remaining() != 0)
                {
                    QuicRawConnection.QuicConnectionId quicConnectionId = QuicRawConnection.connectionId(packetRead);
                    QuicRawConnection connection = _connections.get(quicConnectionId);
                    if (connection != null)
                    {
                        connection.recv(packetRead);

                        Iterator<QuicStream> it = connection.readableStreamsIterator();
                        while (it.hasNext())
                        {
                            QuicStream stream = it.next();
                            long streamId = stream.getStreamId();
                            AbstractEndPoint endPoint = _endpoints2.get(streamId);

                            // the following 3 lines could go into fill()
//                            byte[] buf = new byte[8192];
//                            stream.read(buf);
//                            endPoint.dataArrived(buf);

                            endPoint.getFillInterest().fillable();
                        }
                        //TODO figure out if we need to write

                    }
                    else
                    {
                        //TODO this buffer has to be released eventually
                        ByteBuffer packetToWrite = getByteBufferPool().acquire(1500, true); //TODO replace 1500 with MTU
                        QuicRawConnection newQuicConnection = QuicRawConnection.tryAccept(null, peer, packetRead, packetToWrite);
                        packetToWrite.flip();
                        //TODO what if it does not write?
                        int written = _acceptChannel.send(packetToWrite, peer);
                        packetToWrite.clear();
                        if (newQuicConnection != null)
                        {
                            _connections.put(quicConnectionId, newQuicConnection);
                        }
                    }

                }

            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            getByteBufferPool().release(packetRead);
        }

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
//            channel.configureBlocking(true);
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
        channel.configureBlocking(false);
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
