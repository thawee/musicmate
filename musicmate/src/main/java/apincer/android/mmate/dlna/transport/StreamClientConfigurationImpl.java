package apincer.android.mmate.dlna.transport;


import android.os.Build;

import org.jupnp.model.ServerClientTokens;
import org.jupnp.transport.spi.AbstractStreamClientConfiguration;

import java.util.concurrent.ExecutorService;

public class StreamClientConfigurationImpl extends AbstractStreamClientConfiguration {

    public StreamClientConfigurationImpl(ExecutorService requestExecutorService, int timeoutSeconds, int logWarningSeconds) {
        super(requestExecutorService, timeoutSeconds, logWarningSeconds);
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