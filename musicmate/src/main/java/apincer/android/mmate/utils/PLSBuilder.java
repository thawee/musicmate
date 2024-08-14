package apincer.android.mmate.utils;

import static apincer.android.mmate.utils.MusicTagUtils.getExtension;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.repository.MusicTag;

public class PLSBuilder {
    public static String build(String baseUri, List<MusicTag> tags) {
        StringBuffer buff = new StringBuffer();
        buff.append("[playlist]").append("\n");
        int fileCnt =1;
        baseUri = buildUri(baseUri);
        for(MusicTag tag: tags) {
            try {
                String filename = getFilename(tag);
                String uri = String.format(Locale.US, "%s/%s", baseUri, filename);
                String link = new URI(null, uri, null).toASCIIString();

                buff.append(String.format(Locale.US, "File%d=%s", fileCnt, link)).append("\n");
                buff.append(String.format(Locale.US, "Title%d=%s", fileCnt, getTitle(tag))).append("\n");
                buff.append(String.format(Locale.US, "Length%d=%.0f", fileCnt, tag.getAudioDuration())).append("\n");
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            fileCnt++;
        }
        buff.append("NumberOfEntries=").append(tags.size()).append("\n");
        buff.append("Version=2").append("\n");
        return buff.toString();
    }

    private static Object getTitle(MusicTag tag) {
        return String.format(Locale.US, "%s - %s", tag.getArtist(), tag.getTitle());
    }

    private static String buildUri(String uri) {
        if(!uri.endsWith("/")) {
            uri += "/";
        }
        return uri;
    }

    private static String getFilename(MusicTag tag) {
      return tag.getId()+ "." + getExtension(tag);
    }

}
