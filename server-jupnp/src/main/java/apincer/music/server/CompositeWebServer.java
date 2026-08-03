package apincer.music.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

import apincer.music.core.Constants;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.spi.WebServer;

public class CompositeWebServer implements WebServer {
    private static final String TAG = "CompositeWebServer";
    private final Context context;
    private final FileRepository fileRepos;
    private final TagRepository tagRepos;
    private final SharedPreferences prefs;

    private WebServer activeEngine;
    private String currentEngineKey = "";

    public CompositeWebServer(Context context, FileRepository fileRepos, TagRepository tagRepos) {
        this.context = context;
        this.fileRepos = fileRepos;
        this.tagRepos = tagRepos;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.activeEngine = createEngine();
    }

    private synchronized WebServer createEngine() {
        String engineKey = prefs.getString(Constants.PREF_SERVER_ENGINE, "httpcore");
        currentEngineKey = engineKey;

        Log.d(TAG, "Creating server engine: " + engineKey);
        try {
            switch (engineKey.toLowerCase()) {
                case "nio": {
                    Class<?> clazz = Class.forName("apincer.music.server.nio.NioWebServerImpl");
                    return (WebServer) clazz.getConstructor(Context.class, FileRepository.class, TagRepository.class)
                            .newInstance(context, fileRepos, tagRepos);
                }
                case "netty": {
                    Class<?> clazz = Class.forName("apincer.android.jupnp.server.netty.NettyWebServerImpl");
                    return (WebServer) clazz.getConstructor(Context.class, FileRepository.class, TagRepository.class)
                            .newInstance(context, fileRepos, tagRepos);
                }
                case "httpcore":
                default: {
                    Class<?> clazz = Class.forName("apincer.android.jupnp.server.httpcore.HttpCoreWebServerImpl");
                    return (WebServer) clazz.getConstructor(Context.class, FileRepository.class, TagRepository.class)
                            .newInstance(context, fileRepos, tagRepos);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to instantiate engine '" + engineKey + "', falling back to NioWebServerImpl", e);
            return new apincer.music.server.nio.NioWebServerImpl(context, fileRepos, tagRepos);
        }
    }

    private synchronized void checkAndUpdateEngine() {
        String engineKey = prefs.getString(Constants.PREF_SERVER_ENGINE, "httpcore");
        if (!engineKey.equalsIgnoreCase(currentEngineKey)) {
            Log.d(TAG, "Engine preference changed from " + currentEngineKey + " to " + engineKey);
            if (activeEngine != null) {
                try {
                    activeEngine.stopServer();
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping previous engine", e);
                }
            }
            activeEngine = createEngine();
        }
    }

    @Override
    public synchronized void initServer(InetAddress bindAddress) throws Exception {
        checkAndUpdateEngine();
        if (activeEngine != null) {
            activeEngine.initServer(bindAddress);
        }
    }

    @Override
    public synchronized void stopServer() {
        if (activeEngine != null) {
            activeEngine.stopServer();
        }
    }

    @Override
    public synchronized void restartServer(InetAddress bindAddress) throws Exception {
        checkAndUpdateEngine();
        if (activeEngine != null) {
            activeEngine.restartServer(bindAddress);
        }
    }

    @Override
    public int getListenPort() {
        if (activeEngine != null) {
            return activeEngine.getListenPort();
        }
        return 8080;
    }

    @Override
    public List<String> getLibInfos() {
        if (activeEngine != null) {
            return activeEngine.getLibInfos();
        }
        return Collections.emptyList();
    }
}
