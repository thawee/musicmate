package apincer.android.mmate.server.undertow;

import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.util.IO;
import org.fourthline.cling.model.message.Connection;
import org.fourthline.cling.model.message.StreamRequestMessage;
import org.fourthline.cling.model.message.StreamResponseMessage;
import org.fourthline.cling.model.message.UpnpHeaders;
import org.fourthline.cling.model.message.UpnpMessage;
import org.fourthline.cling.model.message.UpnpRequest;
import org.fourthline.cling.protocol.ProtocolFactory;
import org.fourthline.cling.transport.spi.UpnpStream;

import io.undertow.server.BlockingHttpExchange;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * @author Jason Mahdjoub
 * @since 1.2.0
 */
public abstract class UndertowUpnpStream extends UpnpStream {

    final private static String TAG = "UndertowUpnpStream";

    private final HttpServerExchange httpExchange;

    public UndertowUpnpStream(ProtocolFactory protocolFactory, HttpServerExchange httpExchange) {
        super(protocolFactory);
        this.httpExchange = httpExchange;
    }

    public HttpServerExchange getHttpExchange() {
        return httpExchange;
    }

    private UpnpRequest.Method getRequestMethod()
    {
        return UpnpRequest.Method.getByHttpName(getHttpExchange().getRequestMethod().toString());
    }
    private URI getRequestURI() throws URISyntaxException {
        return new URI(getHttpExchange().getRequestURI());
    }
    private String getProtocol()
    {
        return getHttpExchange().getProtocol().toString();
    }
    private Map<String, List<String>> getRequestHeaders()
    {
        return getRequestHeaders(getHttpExchange().getRequestHeaders());
    }

    static Map<String, List<String>> getRequestHeaders(HeaderMap headerMap)
    {
        Map<String, List<String>> m=new HashMap<>();
        for (HeaderValues hv : headerMap)
        {
            String k=hv.getHeaderName().toString();
            List<String> list=new ArrayList<>(hv.size());
            list.addAll(hv);
            m.put(k, list);
        }
        return m;
    }

    static void putAll(HeaderMap hm, UpnpHeaders headers)
    {
      /*  for (Map.Entry<String, List<String>> e : headers. .entrySet())
        {
            HeaderValues hv=hm.get(e.getKey());
            hv.addAll(e.getValue());
        } */
    }

    static void putAll(HeaderMap hm, Map<String, List<String>> m)
    {
        for (Map.Entry<String, List<String>> e : m.entrySet())
        {
            HeaderValues hv=hm.get(e.getKey());
            hv.addAll(e.getValue());
        }
    }

    @Override
    public void run() {
        try {
            final HttpServerExchange httpExchange=getHttpExchange();

            Log.d(TAG, "Processing HTTP request: " + httpExchange.getRequestMethod() + " " + httpExchange.getRequestURI());

            // Status
            StreamRequestMessage requestMessage =
                    new StreamRequestMessage(
                            getRequestMethod(),
                            getRequestURI()
                    );

            if (requestMessage.getOperation().getMethod().equals(UpnpRequest.Method.UNKNOWN)) {
                Log.d(TAG,"Method not supported by UPnP stack: " + httpExchange.getRequestMethod());
                throw new RuntimeException("Method not supported: " + httpExchange.getRequestMethod());
            }

            // Protocol
            requestMessage.getOperation().setHttpMinorVersion(
                    "HTTP/1.1".equals(getProtocol().toUpperCase(Locale.ROOT)) ? 1 : 0
            );

            Log.d(TAG,"Created new request message: " + requestMessage);

            // Connection wrapper
            requestMessage.setConnection(createConnection());

            // Headers
            requestMessage.setHeaders(new UpnpHeaders(getRequestHeaders()));

            // Body
            httpExchange.dispatch(() -> {
                byte[] bodyBytes;
                try {
                    try (BlockingHttpExchange bhe = httpExchange.startBlocking(); InputStream is = bhe == null ? httpExchange.getInputStream() : bhe.getInputStream()) {
                        bodyBytes = IO.readBytes(is);

                    }
                    Log.d(TAG,"Reading request body bytes: " + bodyBytes.length);

                    if (bodyBytes.length > 0 && requestMessage.isContentTypeMissingOrText()) {
                        Log.d(TAG,"Request contains textual entity body, converting then setting string on message");
                        requestMessage.setBodyCharacters(bodyBytes);

                    } else if (bodyBytes.length > 0) {
                        Log.d(TAG,"Request contains binary entity body, setting bytes on message");
                        requestMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);

                    } else {
                        Log.d(TAG,"Request did not contain entity body");
                    }

                    // Process it
                    StreamResponseMessage responseMessage = process(requestMessage);

                    // Return the response
                    if (responseMessage != null) {
                        Log.d(TAG,"Preparing HTTP response message: " + responseMessage);

                        // Headers
                        //putAll(httpExchange.getResponseHeaders(), responseMessage.getHeaders(), log.isDebugEnabled() ? log : null);
                        putAll(httpExchange.getResponseHeaders(), responseMessage.getHeaders());

                        // Body
                        byte[] responseBodyBytes = responseMessage.hasBody() ? responseMessage.getBodyBytes() : null;
                        int contentLength = responseBodyBytes != null ? responseBodyBytes.length : -1;

                        Log.d(TAG,"Sending HTTP response message: " + responseMessage + " with content length: " + contentLength);

                        httpExchange.setStatusCode(responseMessage.getOperation().getStatusCode())
                                .setResponseContentLength(contentLength);

                        if (contentLength > 0) {
                            Log.d(TAG,"Response message has body, writing bytes to stream...");
                            try (OutputStream os = getHttpExchange().getOutputStream()) {
                                IOUtils.write(responseBodyBytes, os);
                                os.flush();
                            }
                        }

                    } else {
                        // If it's null, it's 404, everything else needs a proper httpResponse
                        Log.d(TAG,"Sending HTTP response status: " + HttpURLConnection.HTTP_NOT_FOUND);
                        httpExchange.setStatusCode(HttpURLConnection.HTTP_NOT_FOUND)
                                .setResponseContentLength(-1);
                    }
                    httpExchange.endExchange();
                    responseSent(responseMessage);
                }
                catch (Throwable t) {
                    throwDuringRun(t);
                }
            });
        } catch (Throwable t) {
            throwDuringRun(t);
        }
    }

    private void throwDuringRun(Throwable t) {
        // You definitely want to catch all Exceptions here, otherwise the server will
        // simply close the socket, and you get an "unexpected end of file" on the client.
        // The same is true if you just rethrow an IOException - it is a mystery why it
        // is declared then on the HttpHandler interface if it isn't handled in any
        // way... so we always do error handling here.

        // TODO: We should only send an error if the problem was on our side
        // You don't have to catch Throwable unless, like we do here in unit tests,
        // you might run into Errors as well (assertions).
        Log.e(TAG,"Exception occured during UPnP stream processing: ", t);

        getHttpExchange().setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);

        responseException(t);
    }

    abstract protected Connection createConnection();
}