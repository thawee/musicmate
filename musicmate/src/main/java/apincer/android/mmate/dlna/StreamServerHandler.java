package apincer.android.mmate.dlna;

import android.util.Log;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.transport.spi.UpnpStream;
import org.jupnp.util.Exceptions;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import apincer.android.mmate.dlna.content.ContentBrowser;

public class StreamServerHandler extends UpnpStream implements AsyncServerRequestHandler<Message<HttpRequest, byte[]>> {
    private static final String TAG = "StreamServerHandler";
    protected StreamServerHandler(ProtocolFactory protocolFactory ) {
        super(protocolFactory);
    }

    @Override
    public AsyncRequestConsumer<Message<HttpRequest, byte[]>> prepare(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final HttpContext context) throws HttpException {
        return new BasicRequestConsumer<>(entityDetails != null ? new BasicAsyncEntityConsumer() : null);
    }

    @Override
    public void handle(
            final Message<HttpRequest, byte[]> message,
            final ResponseTrigger responseTrigger,
            final HttpContext context) throws HttpException, IOException {
        final AsyncResponseBuilder responseBuilder = AsyncResponseBuilder.create(HttpStatus.SC_OK);

        try {
            StreamRequestMessage requestMessage = readRequestMessage(message);
            Log.v(TAG, "Processing new request message: " + requestMessage);
            if("CyberGarage-HTTP/1.0".equals(requestMessage.getHeaders().getFirstHeader("User-agent"))) {
                Log.v(TAG, "Temp FIX for MConnect, show only 20 songs");
                ContentBrowser.forceFullContent = true;
            }

            StreamResponseMessage responseMessage = process(requestMessage);

            if (responseMessage != null) {

                Log.v(TAG, "Preparing HTTP response message: " + responseMessage);
                writeResponseMessage(responseMessage, responseBuilder);
            } else {
                // If it's null, it's 404
                Log.v(TAG, "Sending HTTP response status: " + HttpStatus.SC_NOT_FOUND);
                responseBuilder.setStatus(HttpStatus.SC_NOT_FOUND);
            }
            responseTrigger.submitResponse(responseBuilder.build(), context);

        } catch (Throwable t) {
           // StreamRequestMessage requestMessage = readRequestMessage(message);
            Log.e(TAG, "Exception occurred during UPnP stream processing: ", t);
           // Log.d(TAG, "Cause: " + Exceptions.unwrap(t), Exceptions.unwrap(t));
            Log.v(TAG, "returning INTERNAL SERVER ERROR to client");
            responseBuilder.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            responseTrigger.submitResponse(responseBuilder.build(), context);

            responseException(t);
        }
    }

    protected StreamRequestMessage readRequestMessage(Message<HttpRequest, byte[]> message) throws IOException {
        // Extract what we need from the HTTP httpRequest
        HttpRequest request = message.getHead();
        String requestMethod = request.getMethod();
        String requestURI = request.getRequestUri();

        Log.v(TAG, "Processing HTTP request: " + requestMethod + " " + requestURI);

        StreamRequestMessage requestMessage;
        try {
            requestMessage =
                    new StreamRequestMessage(
                            UpnpRequest.Method.getByHttpName(requestMethod),
                            URI.create(requestURI)
                    );

        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid request URI: " + requestURI, ex);
        }

        if (requestMessage.getOperation().getMethod().equals(UpnpRequest.Method.UNKNOWN)) {
            throw new RuntimeException("Method not supported: " + requestMethod);
        }

        UpnpHeaders headers = new UpnpHeaders();
        Header[] requestHeaders = request.getHeaders();
        for (Header header : requestHeaders) {
            headers.add(header.getName(), header.getValue());
        }
        requestMessage.setHeaders(headers);

        // Body
        byte[] bodyBytes = message.getBody();
        if (bodyBytes == null) {
            bodyBytes = new byte[]{};
        }
        Log.v(TAG, "Reading request body bytes: " + bodyBytes.length);

        if (bodyBytes.length > 0 && requestMessage.isContentTypeMissingOrText()) {

            Log.v(TAG, "Request contains textual entity body, converting then setting string on message");
            requestMessage.setBodyCharacters(bodyBytes);

        } else if (bodyBytes.length > 0) {

            Log.v(TAG, "Request contains binary entity body, setting bytes on message");
            requestMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);

        } else {
            Log.v(TAG, "Request did not contain entity body");
        }

        return requestMessage;
    }

    protected void writeResponseMessage(StreamResponseMessage responseMessage, AsyncResponseBuilder responseBuilder) {
        Log.v(TAG, "Sending HTTP response status: " + responseMessage.getOperation().getStatusCode());

        responseBuilder.setStatus(responseMessage.getOperation().getStatusCode());

        // Headers
        for (Map.Entry<String, List<String>> entry : responseMessage.getHeaders().entrySet()) {
            for (String value : entry.getValue()) {
                responseBuilder.addHeader(entry.getKey(), value);
            }
        }
        // The Date header is recommended in UDA
        responseBuilder.addHeader("Date", "" + System.currentTimeMillis());

        // Body
        byte[] responseBodyBytes = responseMessage.hasBody() ? responseMessage.getBodyBytes() : null;
        int contentLength = responseBodyBytes != null ? responseBodyBytes.length : -1;

        if (contentLength > 0) {
            Log.v(TAG, "Response message has body, writing bytes to stream...");
            ContentType ct = ContentType.APPLICATION_XML;
            if (responseMessage.getContentTypeHeader() != null) {
                ct = ContentType.parse(responseMessage.getContentTypeHeader().getValue().toString());
            }
            responseBuilder.setEntity(AsyncEntityProducers.create(responseBodyBytes, ct));
        }
    }

    @Override
    public void run() {

    }
}