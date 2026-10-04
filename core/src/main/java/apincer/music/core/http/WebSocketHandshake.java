package apincer.music.core.http;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** RFC 6455 opening handshake: computes Sec-WebSocket-Accept for an Upgrade request. */
final class WebSocketHandshake {
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    static NioHttpServer.HttpResponse createHandshakeResponse(NioHttpServer.HttpRequest request) throws NoSuchAlgorithmException {
        String clientKey = request.getHeader("sec-websocket-key", null);
        if (clientKey == null) {
            return new NioHttpServer.HttpResponse().setStatus(NioHttpServer.HTTP_BAD_REQUEST, "Bad Request").setBody("Missing Sec-WebSocket-Key header".getBytes());
        }
        String acceptKey = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((clientKey + WEBSOCKET_GUID).getBytes(StandardCharsets.UTF_8))
        );
        return new NioHttpServer.HttpResponse()
                .setStatus(NioHttpServer.HTTP_SWITCHING_PROTOCOLS, "Switching Protocols")
                .addHeader("Upgrade", "websocket")
                .addHeader("Connection", "Upgrade")
                .addHeader("Sec-WebSocket-Accept", acceptKey);
    }
}
