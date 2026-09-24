package org.apache.hc.core5.util;

import org.apache.hc.core5.annotation.Internal;
import java.lang.reflect.Method;
import java.net.Socket;

/**
 * Android-safe replacement for Apache HttpCore 5 ReflectionUtils.
 * Avoids direct static linking to jdk.net.Sockets and jdk.net.ExtendedSocketOptions,
 * which are blocked hidden APIs on modern Android (TargetSdk 35+, ART core-platform blocked)
 * and trigger fatal NoSuchMethodError during class initialization.
 */
@Internal
@SuppressWarnings("Since15")
public final class ReflectionUtils {
    private static final boolean SUPPORTS_KEEPALIVE_OPTIONS;

    static {
        boolean supported = false;
        boolean isAndroid = false;
        try {
            Class.forName("android.os.Build");
            isAndroid = true;
        } catch (Throwable ignored) {
        }

        if (!isAndroid) {
            try {
                // On standard JVM, check dynamically via reflection rather than static linking.
                Class<?> socketsClass = Class.forName("jdk.net.Sockets");
                Class<?> extendedOptionsClass = Class.forName("jdk.net.ExtendedSocketOptions");
                Method supportedOptionsMethod = socketsClass.getMethod("supportedOptions", Class.class);
                java.util.Set<?> options = (java.util.Set<?>) supportedOptionsMethod.invoke(null, Socket.class);
                if (options != null) {
                    Object keepIdle = extendedOptionsClass.getField("TCP_KEEPIDLE").get(null);
                    Object keepInterval = extendedOptionsClass.getField("TCP_KEEPINTERVAL").get(null);
                    Object keepCount = extendedOptionsClass.getField("TCP_KEEPCOUNT").get(null);
                    supported = options.contains(keepIdle) && options.contains(keepInterval) && options.contains(keepCount);
                }
            } catch (Throwable ignored) {
                supported = false;
            }
        }
        SUPPORTS_KEEPALIVE_OPTIONS = supported;
    }

    public static void callSetter(final Object object, final String setterName, final Class<?> type, final Object value) {
        try {
            final Class<?> clazz = object.getClass();
            final Method method = clazz.getMethod("set" + setterName, type);
            method.setAccessible(true);
            method.invoke(object, value);
        } catch (final Exception ignore) {
        }
    }

    public static <T> T callGetter(final Object object, final String getterName, final Class<T> resultType) {
        return callGetter(object, getterName, null, null, resultType);
    }

    public static <T> T callGetter(final Object object, final String getterName, final Object arg, final Class<?> argType, final Class<T> resultType) {
        try {
            final Class<?> clazz = object.getClass();
            final Method method;
            if (arg != null) {
                assert argType != null;
                method = clazz.getMethod("get" + getterName, argType);
                method.setAccessible(true);
                return resultType.cast(method.invoke(object, arg));
            } else {
                assert argType == null;
                method = clazz.getMethod("get" + getterName);
                method.setAccessible(true);
                return resultType.cast(method.invoke(object));
            }
        } catch (final Exception ignore) {
            return null;
        }
    }

    public static int determineJRELevel() {
        final String s = System.getProperty("java.version");
        final String[] parts = s.split("\\.");
        if (parts.length > 0) {
            try {
                final int majorVersion = Integer.parseInt(parts[0]);
                if (majorVersion > 1) {
                    return majorVersion;
                } else if (majorVersion == 1 && parts.length > 1) {
                    return Integer.parseInt(parts[1]);
                }
            } catch (final NumberFormatException ignore) {
            }
        }
        return 7;
    }

    public static boolean supportsKeepAliveOptions() {
        return SUPPORTS_KEEPALIVE_OPTIONS;
    }
}