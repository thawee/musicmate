package apincer.android.mmate.dlna;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.StringUtils;

public class MediaServerSession {
    private static final List<String> transCodeList = new ArrayList<>();
    public static boolean forceFullContent = false;
    public static String streamServerHost = "";

    static {
       // transCodeList.add("MPEG");
        transCodeList.add("AAC");
    }

    public static boolean isTransCoded(MusicTag tag) {
        String enc = tag.getAudioEncoding();
        if(!StringUtils.isEmpty(enc)) {
            enc = enc.toUpperCase(Locale.US);
            return transCodeList.contains(enc);
        }
        return false;
    }

    public static String getIpAddress() {
        return streamServerHost;
    }
}
