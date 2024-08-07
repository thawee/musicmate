package apincer.android.mmate.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogHelper {
    public static final String LOGGER_JAUDIOTAGGER = "org.jaudiotagger";
    public static final String LOGGER_JAUDIOTAGGER_AUDIO = "org.jaudiotagger.audio";
    public static final String LOGGER_JAUDIOTAGGER_FLAC = "org.jaudiotagger.auddio.flac";
    public static final String LOGGER_SLF4J_ANDROID = "apincer.android.slf4j";
    public static final String LOGGER_ANDROID_NOTIFICATION = "com.android.server.notification";
    public static void initial() {
        setLogLevel(LOGGER_JAUDIOTAGGER, Level.SEVERE);
        setLogLevel(LOGGER_JAUDIOTAGGER_AUDIO, Level.SEVERE);
        setLogLevel(LOGGER_JAUDIOTAGGER_FLAC, Level.SEVERE);
        setLogLevel("android.view", Level.SEVERE);
        setLogLevel("androidx.recyclerview", Level.SEVERE);
        setLogLevel("org.greenrobot.eventbus", Level.SEVERE);

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
        setLogLevel("apincer.android.mmate.dlna.transport.AndroidRouter", Level.CONFIG);
    }

    public static String getTag(Class cls) {
        return cls.getSimpleName();
    }
}
