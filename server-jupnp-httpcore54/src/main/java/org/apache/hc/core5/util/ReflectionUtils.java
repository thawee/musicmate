package org.apache.hc.core5.util;

import java.lang.reflect.Method;

import org.apache.hc.core5.annotation.Internal;

@Internal
public final class ReflectionUtils {

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

    /**
     * @param <T> The return type.
     * @since 5.3
     */
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
        // hacked to run on android
        return false;
    }

}