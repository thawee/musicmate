package apincer.android.mqaidentifier;

public class NativeLib {

    // Used to load the 'mqaidentifier' library on application startup.
    static {
        System.loadLibrary("mqaidentifier");
    }

    /**
     * A native method that is implemented by the 'mqaidentifier' native library,
     * which is packaged with this application.
     */
   // public native String stringFromJNI();
    public native String getMQAInfo(String flac);
}