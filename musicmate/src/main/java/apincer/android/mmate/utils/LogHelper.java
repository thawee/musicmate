package apincer.android.mmate.utils;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogHelper {
    public static final String LOGGER_JAUDIOTAGGER = "org.jaudiotagger";
    public static final String LOGGER_JAUDIOTAGGER_AUDIO = "org.jaudiotagger.audio";
    public static final String LOGGER_JAUDIOTAGGER_FLAC = "org.jaudiotagger.audio.flac";
    public static final String LOGGER_JAUDIOTAGGER_WAV = "org.jaudiotagger.audio.wav";
    public static final String LOGGER_SLF4J_ANDROID = "apincer.android.slf4j";
    public static void initial() {
        setLogLevel(LOGGER_JAUDIOTAGGER, Level.SEVERE);
        setLogLevel(LOGGER_JAUDIOTAGGER_AUDIO, Level.SEVERE);
        setLogLevel(LOGGER_JAUDIOTAGGER_FLAC, Level.SEVERE);
        setLogLevel(LOGGER_JAUDIOTAGGER_WAV, Level.SEVERE);
        setLogLevel("android.view.View", Level.SEVERE);
        setLogLevel("androidx.recyclerview.selection.DefaultSelectionTracker", Level.SEVERE);
        setLogLevel("org.greenrobot.eventbus.EventBus", Level.SEVERE);
       // setLogLevel(" io.netty", Level.FINE);

        setLogLevel("apincer.android.mmate.repository.FFMPegReader", Level.SEVERE);
        setLogLevel("apincer.android.mmate.dlna.content.ContentDirectory", Level.WARNING);
        setLogLevel("apincer.android.mmate.dlna.transport.NettyStreamServer", Level.SEVERE);
        setLogLevel("apincer.android.mmate.dlna.transport.HttpCoreStreamServer", Level.SEVERE);
        setLogLevel("apincer.android.mmate.repository.JAudioTaggerReader", Level.SEVERE);
        setLogLevel("apincer.android.mmate.provider.CoverArtProvider", Level.OFF);
        setLogLevel("apincer.android.mmate.dlna.transport.OKHttpUPnpStreamingClient", Level.OFF);
    }

    public static void setLogLevel(String loggerName, Level level) {
        Logger logger = Logger.getLogger(loggerName);
        logger.setLevel(level);
    }

    public static void setSLF4JOn() {
       // setLog(LOGGER_SLF4J_ANDROID, Level.INFO);
        setLogLevel("com.j256.ormlite.stmt", Level.INFO);
        setLogLevel("org.jupnp", Level.INFO);
        setLogLevel("org.jupnp.transport.impl.NetworkAddressFactoryImpl", Level.SEVERE);
        setLogLevel("apincer.android.mmate.dlna.android.AndroidRouter", Level.CONFIG);
    }

    public static void setFFMpegOn() {
        FFmpegKitConfig.setLogLevel(com.arthenica.ffmpegkit.Level.AV_LOG_INFO);
    }

    public static void setFFMpegOff() {
        FFmpegKitConfig.setLogLevel(com.arthenica.ffmpegkit.Level.AV_LOG_QUIET);
    }

    public static String getTag(Class cls) {
        return cls.getSimpleName();
    }
}
