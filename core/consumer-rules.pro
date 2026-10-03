# FFmpegKit (com.antonkarpenko:ffmpeg-kit-full) ships no keep rules. JNI_OnLoad in
# libffmpegkit_abidetect.so / libffmpegkit.so registers these native methods by name
# and calls the Java callbacks below; if R8 removes or renames any of them,
# JNI_OnLoad fails ("Bad JNI version") and the first FFmpeg use crashes the app.
-keep class com.antonkarpenko.ffmpegkit.AbiDetect {
    native <methods>;
}
-keep class com.antonkarpenko.ffmpegkit.FFmpegKitConfig {
    native <methods>;
    private static void log(long, int, byte[]);
    private static void statistics(long, int, float, float, long, double, double, double);
    private static int safOpen(int);
    private static int safClose(int);
}
