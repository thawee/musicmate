package apincer.android.mmate.server;

import org.jupnp.transport.Router;
import org.jupnp.transport.spi.StreamServerConfiguration;
import org.jupnp.transport.spi.UpnpStream;

public class MusicMateStreamServerConfiguration implements StreamServerConfiguration {

    protected int listenPort;
    protected int asyncTimeoutSeconds = 60;


    public MusicMateStreamServerConfiguration(int listenPort) {
        this.listenPort = listenPort;
    }

    /**
     * @return Defaults to <code>0</code>.
     */
    public int getListenPort() {
        return listenPort;
    }


    /**
     * The time in seconds this server wait for the {@link Router}
     * to execute a {@link UpnpStream}.
     *
     * @return The default of 60 seconds.
     */
    public int getAsyncTimeoutSeconds() {
        return asyncTimeoutSeconds;
    }
}