package apincer.android.slf4j;

final class CallerStackTrace extends Throwable {
    private static final long serialVersionUID = 1L;
    private static final StackTraceElement UNKNOWN = new StackTraceElement("<unknown class>", "<unknown method>", null, -1);
    private final StackTraceElement stackFrame;

    public CallerStackTrace(final int frames) {
        StackTraceElement stackFrame;
        try {
            stackFrame = getStackTrace()[frames];
        } catch (ArrayIndexOutOfBoundsException e) {
            stackFrame = UNKNOWN;
        }
        this.stackFrame = stackFrame;
    }

    public final StackTraceElement get() {
        return stackFrame;
    }

    @Override
    public final String toString() {
        return stackFrame.toString();
    }
}