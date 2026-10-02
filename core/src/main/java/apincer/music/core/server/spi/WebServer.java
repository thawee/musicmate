package apincer.music.core.server.spi;

import java.net.InetAddress;
import java.util.List;

public interface WebServer {
    void restartServer(InetAddress bindAddress) throws Exception;

    void initServer(InetAddress bindAddress) throws Exception;
    void stopServer();
    int getListenPort();

    List<String> getLibInfos();

    /** One-line live summary for the Music Center Server tab, or "" when not running. */
    default String getDiagnostics() {
        return "";
    }
}
