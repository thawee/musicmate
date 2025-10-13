package apincer.android.mmate.server.undertow;

import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.fourthline.cling.model.message.StreamRequestMessage;
import org.fourthline.cling.model.message.StreamResponseMessage;
import org.fourthline.cling.model.message.UpnpHeaders;
import org.fourthline.cling.model.message.UpnpMessage;
import org.fourthline.cling.model.message.UpnpRequest;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.message.header.UpnpHeader;
import org.fourthline.cling.transport.spi.InitializationException;
import org.fourthline.cling.transport.spi.StreamClient;
import org.jupnp.util.Exceptions;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.UndertowClient;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

public class StreamClientImpl implements StreamClient<StreamClientConfigurationImpl> {
    public static String TAG= "StreamClientImpl";
    public static final String COULD_NOT_CREATE_REQUEST = "Could not create request: ";

    final protected StreamClientConfigurationImpl configuration;
    private final UndertowClient client;
    private final OptionMap options;
    public StreamClientImpl(StreamClientConfigurationImpl configuration) throws InitializationException {
        this.configuration = configuration;

        Log.d(TAG, "Using persistent HTTP stream client connections: " + configuration.isUsePersistentConnections());

        Log.i(TAG, "Starting Undertow HttpClient...");
        this.client = UndertowClient.getInstance();
        int timeout=configuration.getTimeoutSeconds();

        options = OptionMap.builder()
                .set(Options.READ_TIMEOUT, timeout*1000)
                .set(Options.WRITE_TIMEOUT, timeout*1000)
                .set(Options.SSL_CLIENT_SESSION_TIMEOUT, timeout)
                .set(Options.SSL_SERVER_SESSION_TIMEOUT, timeout)
                .getMap();
    }

    @Override
    public StreamClientConfigurationImpl getConfiguration() {
        return configuration;
    }

    @Override
    public StreamResponseMessage sendRequest(StreamRequestMessage requestMessage) throws InterruptedException {

        final UpnpRequest requestOperation = requestMessage.getOperation();
        Log.d(TAG, "Preparing HTTP request message with method '" + requestOperation.getHttpMethodName() + "': " + requestMessage);

        IoFuture<ClientConnection> connectionFuture=null;
        CompletableFuture<StreamResponseMessage> responseFuture=null;
        try (ByteBufferPool bufferPool = new DefaultByteBufferPool(true, 4096)){

            int timeout=configuration.getTimeoutSeconds();

            connectionFuture= client.connect(requestOperation.getURI(), configuration.getRequestExecutorService(), bufferPool, options);

            if (connectionFuture.await(timeout, TimeUnit.SECONDS)==IoFuture.Status.DONE) {
                try(ClientConnection connection=connectionFuture.get()) {
                    connectionFuture = null;

                    ClientRequest request = new ClientRequest()
                            .setPath(requestOperation.getURI().getPath())
                            .setMethod(Objects.requireNonNull(Methods.fromString(requestOperation.getHttpMethodName())));


                    applyRequestProperties(request, requestMessage, requestOperation);
                    responseFuture = new CompletableFuture<>();
                    applyRequestBodyAndGetResponse(connection, request, requestMessage, responseFuture);
                    StreamResponseMessage r = responseFuture.get();
                    responseFuture = null;
                    return r;
                }
            }
            else {
                Log.w(TAG, "HTTP request failed: " + requestMessage);
                return null;
            }

        }
        catch (InterruptedException ex) {
            Log.w(TAG, "Interruption, aborting request: " + requestMessage);

            throw new InterruptedException("HTTP request interrupted and aborted");

        }
        catch (ProtocolException ex) {
            Log.w(TAG, "HTTP request failed: " + requestMessage, Exceptions.unwrap(ex));
            return null;
        }
        catch (IOException ex) {

            Log.d(TAG, "Exception occurred, trying to read the error stream: ", Exceptions.unwrap(ex));
            return null;
        }
        catch (Exception ex) {
            Log.w(TAG, "HTTP request failed: " + requestMessage, Exceptions.unwrap(ex));
            return null;

        } finally {

            if (connectionFuture != null) {
                connectionFuture.cancel();
            }
            if (responseFuture!=null) {

                responseFuture.cancel(true);

            }
        }
    }

    @Override
    public void stop() {
        // NOOP
    }

    protected void applyRequestProperties(ClientRequest request, StreamRequestMessage requestMessage, UpnpRequest requestOperation) {

        // HttpURLConnection always adds a "Host" header

        // HttpURLConnection always adds an "Accept" header (not needed but shouldn't hurt)

        // Add the default user agent if not already set on the message
        UpnpHeaders headers = requestMessage.getHeaders();
        if (!headers.containsKey(UpnpHeader.Type.USER_AGENT)) {
            request.getRequestHeaders().put(Headers.USER_AGENT, getConfiguration().getUserAgentValue(
                    requestMessage.getUdaMajorVersion(),
                    requestMessage.getUdaMinorVersion()));
        }

        // Other headers
        applyHeaders(request, requestMessage.getHeaders(), requestOperation);
    }

    protected void applyHeaders(ClientRequest request, UpnpHeaders headers, UpnpRequest requestOperation) {
        Log.d(TAG, "Writing headers on HttpURLConnection: " + headers);
        UndertowUpnpStream.putAll(request.getRequestHeaders(), headers);
        request.getRequestHeaders().put(Headers.HOST, requestOperation.getURI().getHost());
    }

    protected void applyRequestBodyAndGetResponse(ClientConnection connection, ClientRequest request, StreamRequestMessage requestMessage, CompletableFuture<StreamResponseMessage> responseFuture) throws IOException {
        Log.d(TAG,"Sending HTTP request: " + requestMessage);
        byte[] bytes;
        String contentLength;
        if (requestMessage.hasBody()) {
            if (requestMessage.getBodyType() == UpnpMessage.BodyType.STRING) {
                String charset = requestMessage.getContentTypeCharset() != null
                        ? requestMessage.getContentTypeCharset()
                        : StandardCharsets.UTF_8.toString();
                bytes = requestMessage.getBodyString().getBytes(charset);

            } else {
                bytes = requestMessage.getBodyBytes();
            }
            contentLength=String.valueOf(bytes.length);

        }
        else {
            bytes=null;
            contentLength="0";
        }

        request.getRequestHeaders().put(Headers.CONTENT_LENGTH, contentLength);

        connection.sendRequest(request, new ClientCallback<>() {
            @Override
            public void completed(ClientExchange exchange) {
                try (StreamSinkChannel requestChannel=exchange.getRequestChannel()){

                    if (bytes!=null)
                        requestChannel.write(ByteBuffer.wrap(bytes));
                    requestChannel.shutdownWrites();
                    if (!requestChannel.flush()) {
                        requestChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, null));
                        requestChannel.resumeWrites();
                    }

                    exchange.setResponseListener(new ClientCallback<>() {
                        @Override
                        public void completed(ClientExchange result) {
                            try {

                                responseFuture.complete(createResponse(result, request));
                            } catch (Exception e) {
                                Log.e(TAG,"Failed to read response", e);
                                responseFuture.complete(null);
                            }
                        }

                        @Override
                        public void failed(IOException e) {
                            Log.e(TAG,"Failed to get response", e);
                            responseFuture.complete(null);
                        }
                    });
                } catch (IOException e) {
                    Log.e(TAG,"Failed to write body", e);
                    responseFuture.complete(null);
                }
            }

            @Override
            public void failed(IOException e) {
                Log.e(TAG,"Failed to send request", e);
                responseFuture.complete(null);
            }
        });
    }

    protected StreamResponseMessage createResponse(ClientExchange result, ClientRequest request) throws Exception {

        final ClientResponse response=result.getResponse();
        final int responseCode=response.getResponseCode();

        if (responseCode == -1) {
            Log.e(TAG,"Received an invalid HTTP response: " + request.getPath());
            Log.e(TAG,"Is your DM-UPnP-based server sending connection heartbeats with " +
                        "RemoteClientInfo#isRequestCancelled? This client can't handle " +
                        "heartbeats, read the manual.");
            return null;
        }

        // Status
        UpnpResponse responseOperation = new UpnpResponse(responseCode, response.getStatus());

        Log.d(TAG, "Received response: " + responseOperation);

        // Message
        StreamResponseMessage responseMessage = new StreamResponseMessage(responseOperation);

        // Headers
        responseMessage.setHeaders(new UpnpHeaders(UndertowUpnpStream.getRequestHeaders(response.getResponseHeaders())));

        // Body
        byte[] bodyBytes = null;
        StreamSourceChannel responseChannel = result.getResponseChannel();
        if (responseChannel!=null) {
            try (InputStream is = Channels.newInputStream(responseChannel)) {
                bodyBytes = IOUtils.toByteArray(is);
            }
        }

        if (bodyBytes != null && bodyBytes.length > 0 && responseMessage.isContentTypeMissingOrText()) {
            Log.d(TAG,"Response contains textual entity body, converting then setting string on message");
            responseMessage.setBodyCharacters(bodyBytes);
        } else if (bodyBytes != null && bodyBytes.length > 0) {
            Log.d(TAG,"Response contains binary entity body, setting bytes on message");
            responseMessage.setBody(UpnpMessage.BodyType.BYTES, bodyBytes);
        } else {
            Log.d(TAG,"Response did not contain entity body");
        }

        Log.d(TAG,"Response message complete: " + responseMessage);

        return responseMessage;
    }
}