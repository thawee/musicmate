package apincer.android.musicmeterlib;

public class NativeLib {

    // Used to load the 'musicmeterlib' library on application startup.
    static {
        System.loadLibrary("musicmeterlib");
    }

    /**
     * A native method that is implemented by the 'musicmeterlib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}