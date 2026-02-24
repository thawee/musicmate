package apincer.music.core.server.spi;

import java.net.InetAddress;
import java.util.List;

public interface UpnpServer {
    void initServer(InetAddress bindAddress, Object router) throws Exception;
    void stopServer();
    int getListenPort();

    List<String> getLibInfos();
}
