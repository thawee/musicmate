package apincer.android.mmate.server;
import android.os.Build;


import org.jupnp.model.ServerClientTokens;
import org.jupnp.transport.spi.AbstractStreamClientConfiguration;

import java.util.concurrent.ExecutorService;

public class MusicMateStreamClientConfiguration extends AbstractStreamClientConfiguration {

    public MusicMateStreamClientConfiguration(ExecutorService timeoutExecutorService) {
        super(timeoutExecutorService);
    }


    @Override
    public String getUserAgentValue(int majorVersion, int minorVersion) {
        // TODO: UPNP VIOLATION: Synology NAS requires User-Agent to contain
        // "Android" to return DLNA protocolInfo required to stream to Samsung TV
        // see: http://two-play.com/forums/viewtopic.php?f=6&t=81
        ServerClientTokens tokens = new ServerClientTokens(majorVersion, minorVersion);
        tokens.setOsName("Android");
        tokens.setOsVersion(Build.VERSION.RELEASE);
        return tokens.toString();
    }
}
