package apincer.android.mmate.utils;
import java.util.HashMap;
import java.util.Map;

public class Mime {
   
   public final static Map<String, String> TYPES = new HashMap<String, String>() {{
      put("css", "text/css");
      put("htm", "text/html");
      put("html", "text/html;charset=utf-8");
      put("xml", "text/xml");
      put("java", "text/x-java-source, text/java");
      put("txt", "text/plain");
      put("asc", "text/plain");
      put("gif", "image/gif");
      put("jpg", "image/jpeg");
      put("jpeg", "image/jpeg");
      put("png", "image/png");
      put("aif", "audio/aiff");
      put("aiff", "audio/aiff");
      put("flac", "audio/flac");
      put("m4a", "audio/mp4");
      put("mp3", "audio/mpeg");
      put("m3u", "audio/mpeg-url");
      put("wav", "audio/wav");
      put("wave", "audio/wav");
      put("mp4", "video/mp4");
      put("ogv", "video/ogg");
      put("flv", "video/x-flv");
   }};
   
}
