package apincer.music.core.server.spi;

import java.net.InetAddress;
import java.util.List;

public interface WebServer {
    void initServer(InetAddress bindAddress) throws Exception;
    void stopServer();
    int getListenPort();

    List<String> getLibInfos();
}
