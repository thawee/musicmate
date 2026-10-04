package apincer.android.slf4j;

import android.util.Log;

/**
 * Configuration of Android logging level.
 *
 * Levels are mapped to the corresponding SLF4J level (except ASSERT which does not exist in SLF4J).
 */
public enum LogLevel {
    /** Suppress all log messages. */
    SUPPRESS,

    /** Log messages at ERROR level and above. */
    ERROR,

    /** Log messages at WARN level and above. */
    WARN,

    /** Log messages at INFO level and above. */
    INFO,

    /** Log messages at DEBUG level and above. */
    DEBUG,

    /** Log messages at TRACE level and above. */
    VERBOSE,

    /** Use {@link Log#isLoggable(String, int)} to determine the log level. */
    NATIVE;
}