package apincer.music.core.server.spi;

import java.net.InetAddress;

public interface UpnpServer {
    void initServer(InetAddress bindAddress, Object router) throws Exception;
    void stopServer();
    int getListenPort();
    String getComponentName();
}
