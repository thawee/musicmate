package apincer.music.core.server.spi;

import java.net.InetAddress;

public interface ContentServer {
    void initServer(InetAddress bindAddress) throws Exception;
    void stopServer();
    int getListenPort();
    String getComponentName();
}
