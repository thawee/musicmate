package apincer.android.jupnp.server.httpcore;

import org.apache.hc.core5.util.ReflectionUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ReflectionUtilsTest {

    public static class SampleBean {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Test
    public void testSupportsKeepAliveOptions_doesNotThrow() {
        // Must execute cleanly without NoSuchMethodError or ExceptionInInitializerError
        boolean supported = ReflectionUtils.supportsKeepAliveOptions();
        // Result is either true (on JVM with jdk.net support) or false (on Android / restricted JVM), but must never crash
    }

    @Test
    public void testCallGetterAndSetter() {
        SampleBean bean = new SampleBean();
        ReflectionUtils.callSetter(bean, "Value", String.class, "Test1234");
        assertEquals("Test1234", bean.getValue());

        String retrieved = ReflectionUtils.callGetter(bean, "Value", String.class);
        assertEquals("Test1234", retrieved);
    }

    @Test
    public void testDetermineJRELevel() {
        int level = ReflectionUtils.determineJRELevel();
        assertNotNull(level);
        org.junit.Assert.assertTrue(level >= 7);
    }
}
