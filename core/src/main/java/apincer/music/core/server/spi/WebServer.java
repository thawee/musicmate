package apincer.music.core.server.spi;

import java.net.InetAddress;

public interface WebServer {
    void initServer(InetAddress bindAddress) throws Exception;
    void stopServer();
    int getListenPort();
    String getComponentName();
}
