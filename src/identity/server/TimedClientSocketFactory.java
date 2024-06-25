package identity.server;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.io.IOException;
import java.net.Socket;

/**
 * This class extends the SslRMIClientSocketFactory class and provides a timed client socket factory.
 * It sets the socket timeout to 2 seconds.
 */
public class TimedClientSocketFactory extends SslRMIClientSocketFactory {
    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket socket = super.createSocket(host, port);
        socket.setSoTimeout(2000);
        return socket;
    }
}
