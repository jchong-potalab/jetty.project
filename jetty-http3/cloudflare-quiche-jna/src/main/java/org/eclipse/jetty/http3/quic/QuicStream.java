package org.eclipse.jetty.http3.quic;

import java.io.IOException;

import org.eclipse.jetty.http3.quic.quiche.LibQuiche;
import org.eclipse.jetty.http3.quic.quiche.bool_pointer;
import org.eclipse.jetty.http3.quic.quiche.size_t;
import org.eclipse.jetty.http3.quic.quiche.ssize_t;
import org.eclipse.jetty.http3.quic.quiche.uint64_t;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.http3.quic.quiche.LibQuiche.INSTANCE;

public class QuicStream
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QuicStream.class);

    private final QuicRawConnection.StreamIterator streamIterator;
    private final LibQuiche.quiche_conn quicheConn;
    private final long streamId;
    private boolean receivedFin;

    QuicStream(QuicRawConnection.StreamIterator streamIterator, LibQuiche.quiche_conn quicheConn, long streamId)
    {
        this.streamIterator = streamIterator;
        this.quicheConn = quicheConn;
        this.streamId = streamId;
    }

    public long getStreamId()
    {
        return streamId;
    }

    public int read(byte[] buffer) throws IOException
    {
        bool_pointer fin = new bool_pointer();
        ssize_t recv = INSTANCE.quiche_conn_stream_recv(quicheConn, new uint64_t(streamId), buffer, new size_t(buffer.length), fin);
        LOGGER.debug("Received {} byte(s) from stream {} (fin? {})", recv.intValue(), streamId, fin.getValue());
        receivedFin |= fin.getValue();
        if (recv.intValue() < 0)
        {
            streamIterator.close();
            return 0;
        }
        return recv.intValue();
    }

    public boolean isReceivedFin()
    {
        return receivedFin;
    }
}
