package apincer.android.mmate.dlna.transport;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;

public class NettyContentHolder {
    private final AsciiString contentType;
    private  String filePath;

    public NettyContentHolder(String key, AsciiString contentType, String path) {
        this.contentType = contentType;
        this.filePath = path;
        this.statusCode = HttpResponseStatus.OK;
        this.timestamp = System.currentTimeMillis();
        this.key = key;
        this.content = null;
    }

    public String getFilePath() {
        return filePath;
    }

    private final byte[] content;

    public byte[] getContent() {
        return content;
    }

    public AsciiString getContentType() {
        return contentType;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getKey() {
        return key;
    }

    public HttpResponseStatus getStatusCode() {
        return statusCode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    private final HttpResponseStatus statusCode;
    private final Map<String, String> headers = new HashMap<>();
    final long timestamp;
    final String key;

    public NettyContentHolder(String key, AsciiString mimeType, HttpResponseStatus statusCode, byte[] content) {
        this.content = content;
        this.statusCode = statusCode;
        this.contentType = mimeType;
        this.timestamp = System.currentTimeMillis();
        this.key = key;
    }

    public NettyContentHolder(String key, AsciiString mimeType, HttpResponseStatus statusCode, String content) {
        this.content = content.getBytes(StandardCharsets.UTF_8);
        this.statusCode = statusCode;
        this.contentType = mimeType;
        this.key = key;
        this.timestamp = System.currentTimeMillis();
    }

    /*
    boolean isExpired() {
        return System.currentTimeMillis() - timestamp > NettyUPnpServerImpl.CACHE_EXPIRY_MS &&
                !NettyUPnpServerImpl.DEFAULT_COVERART_KEY.equals(key); // Don't expire default cover art
    } */

    public boolean exists() {
        if (filePath != null) {
            File file = new File(filePath);
            return file.exists() && file.canRead();
        }
        return content!=null && content.length > 0;
    }
}
