package org.eclipse.jetty.http3.server;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.Test;

public class End2EndTest
{
    @Test
    void name() throws Exception
    {
        Server server = new Server();
        QuicConnector quicConnector = new QuicConnector(server);
        quicConnector.setPort(8443);
        server.setConnectors(new Connector[]{quicConnector});

        server.start();

        System.out.println("Started.");
        System.in.read();

        server.stop();
    }
}
