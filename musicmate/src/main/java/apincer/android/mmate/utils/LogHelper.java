package apincer.android.mmate.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogHelper {
    public static final String LOGGER_JAUDIOTAGGER = "org.jaudiotagger";
    public static final String LOGGER_JAUDIOTAGGER_AUDIO = "org.jaudiotagger.audio";
    public static final String LOGGER_JAUDIOTAGGER_FLAC = "org.jaudiotagger.auddio.flac";
    public static final String LOGGER_SLF4J_ANDROID = "apincer.android.slf4j";
    public static final String LOGGER_ANDROID_NOTIFICATION = "com.android.server.notification";
    public static void setJAudioTaggerOff() {
        setLog(LOGGER_JAUDIOTAGGER, Level.SEVERE);
        setLog(LOGGER_JAUDIOTAGGER_AUDIO, Level.SEVERE);
        setLog(LOGGER_JAUDIOTAGGER_FLAC, Level.SEVERE);
    }

    public static void setLog(String loggerName, Level level) {
        Logger logger = Logger.getLogger(loggerName);
        logger.setLevel(level);
    }

    public static void setSLF4JOn() {
       // setLog(LOGGER_SLF4J_ANDROID, Level.INFO);
        setLog("com.j256.ormlite.stmt.SelectIterator", Level.INFO);
        setLog("com.j256.ormlite.stmt.StatementExecutor", Level.INFO);
        setLog("org.jupnp", Level.INFO);
    }

    public static String getTag(Class cls) {
        return cls.getSimpleName();
    }
}
