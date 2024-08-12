package apincer.android.mmate.dlna;

public class MediaServerSession {
    public static boolean forceFullContent = false;
    public static final int streamServerPort = MediaServerConfiguration.STREAM_SERVER_PORT;
    public static String streamServerHost = "";

    public static String getIpAddress() {
        return streamServerHost;
    }

    public static int getListenPort() {
        return streamServerPort;
    }
}
