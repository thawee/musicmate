package apincer.android.slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

/**
 * AndroidLoggerFactory is an implementation of {@link ILoggerFactory} returning
 * the appropriate named {@link LogAdapter} instance.
 *
 * @author Simon Arlott
 */
public final class LoggerFactory implements ILoggerFactory {
    private static final Logger LOG;
    private static final boolean TRACE;
    private static final java.util.logging.Logger alog;
    static {
        alog = java.util.logging.Logger.getLogger("apincer.android.slf4j");
        if(alog.getLevel()== null) {
            alog.setLevel(Level.SEVERE); //default SERVER level, i.e. ERROR
        }
        LOG = new LogAdapter("apincer.android.slf4j", alog.getLevel());
        TRACE = LOG.isTraceEnabled();
    }

    private final ConcurrentMap<String, Logger> loggerMap = new ConcurrentHashMap<>();

    @Override
    public Logger getLogger(final String name) {
        final long start = TRACE ? System.nanoTime() : 0;
        final Logger logger = loggerMap.get(name);
        if (logger != null) {
            if (TRACE) {
                final long stop = System.nanoTime();
                LOG.trace("Found logger {} in {}µs", name, TimeUnit.NANOSECONDS.toMicros(stop - start));
            }
            return logger;
        } else {
            final Logger newInstance = new LogAdapter(name, getLogLevel(name));
            final Logger oldInstance = loggerMap.putIfAbsent(name, newInstance);
            if (TRACE) {
                final long stop = System.nanoTime();
                if (oldInstance == null) {
                    LOG.trace("Created logger {} in {}µs", name, TimeUnit.NANOSECONDS.toMicros(stop - start));
                } else {
                    LOG.trace("Found existing logger {} in {}µs", name, TimeUnit.NANOSECONDS.toMicros(stop - start));
                }
            }
            return oldInstance == null ? newInstance : oldInstance;
        }
    }

    private Level getLogLevel(String name) {
        java.util.logging.Logger logcfg = java.util.logging.Logger.getLogger(name);
        Level level = alog.getLevel();
        if(logcfg.getLevel()==null) {
            while(name.contains(".")) {
                name = name.substring(0, name.lastIndexOf("."));
                logcfg = java.util.logging.Logger.getLogger(name);
                level = logcfg.getLevel();
                if(level != null) break;
            }
        }
        level = (level==null)?alog.getLevel():level;
        return level;
    }

    /**
     * Maximum length of a tag in the Android logging system.
     *
     * This constant is not defined in the API but longer tags will cause exceptions in native code.
     */
    static final int MAX_TAG_LEN = 23;

    /**
     * Create a compatible logging tag for Android based on the logger name.
     */
    static final String createTag(final String name) {
        if (name.length() <= MAX_TAG_LEN) {
            return name;
        }

        final char[] tag = name.toCharArray();
        final int arrayLen = tag.length;
        int len = 0;
        int mark = 0;

        for (int i = 0; i < arrayLen; i++, len++) {
            if (tag[i] == '.') {
                len = mark;

                if (tag[len] != '.') {
                    len++;
                }

                mark = len;

                if (i + 1 < arrayLen && tag[i + 1] != '.') {
                    mark++;
                }
            }

            tag[len] = tag[i];
        }

        if (len > MAX_TAG_LEN) {
            int i = 0;

            mark--;

            for (int j = 0; j < len; j++) {
                if (tag[j] == '.' && ((j != mark) || (i >= MAX_TAG_LEN - 1))) {
                    continue;
                }

                tag[i++] = tag[j];
            }

            len = i;

            if (len > MAX_TAG_LEN) {
                len = MAX_TAG_LEN;
            }
        }

        return new String(tag, 0, len);
    }
}