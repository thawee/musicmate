package apincer.android.mmate.dlna.transport;

import android.util.Log;

import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.message.header.ContentTypeHeader;
import org.jupnp.model.message.header.UpnpHeader;
import org.jupnp.transport.spi.AbstractStreamClient;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.util.MimeType;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OKHttpStreamingClient extends AbstractStreamClient<StreamClientConfigurationImpl, Call> {
    private static final String TAG = "OKHttpStreamingClient";

    final protected StreamClientConfigurationImpl configuration;
    final private OkHttpClient client;

    public OKHttpStreamingClient(StreamClientConfigurationImpl configuration) throws InitializationException {
        this.configuration = configuration;

        // Use the OkHttpClient.Builder to configure the client
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // Use the timeouts from the configuration object
        int timeoutSeconds = getConfiguration().getTimeoutSeconds();
        builder.connectTimeout(timeoutSeconds, TimeUnit.SECONDS);
        builder.readTimeout(timeoutSeconds, TimeUnit.SECONDS);
        builder.writeTimeout(timeoutSeconds, TimeUnit.SECONDS);

        // For UPnP, it's safest to stick to HTTP/1.1 for broad compatibility
        builder.protocols(Collections.singletonList(Protocol.HTTP_1_1));

        this.client = builder.build();
    }

    @Override
    public StreamClientConfigurationImpl getConfiguration() {
        return configuration;
    }

    @Override
    protected Call createRequest(StreamRequestMessage requestMessage) {
        try {
            Request.Builder builder = new Request.Builder();
            URL url = requestMessage.getUri().toURL();
            builder.url(url);

            // Create body first, then set the method once
            RequestBody body = createRequestBody(requestMessage);
            builder.method(requestMessage.getOperation().getHttpMethodName(), body);

            // Set User-Agent if not present
            if (!requestMessage.getHeaders().containsKey(UpnpHeader.Type.USER_AGENT)) {
                String userAgent = getConfiguration().getUserAgentValue(
                        requestMessage.getUdaMajorVersion(),
                        requestMessage.getUdaMinorVersion());
                builder.header(UpnpHeader.Type.USER_AGENT.getHttpName(), userAgent);
            }

            // Set all other headers
            for (Map.Entry<String, List<String>> entry : requestMessage.getHeaders().entrySet()) {
                for (String v : entry.getValue()) {
                    builder.addHeader(entry.getKey(), v);
                }
            }

            // For UPnP, we want to close connections immediately to avoid overwhelming devices
            builder.header("Connection", "close");

            return client.newCall(builder.build());

        } catch (Exception e) {
            Log.e(TAG, "Error creating request", e);
            throw new RuntimeException(e);
        }
    }

    private RequestBody createRequestBody(StreamRequestMessage upnpMessage) {
        if (!upnpMessage.hasBody()) {
            return null;
        }

        MimeType mimeType = upnpMessage.getContentTypeHeader() != null ?
                upnpMessage.getContentTypeHeader().getValue() : ContentTypeHeader.DEFAULT_CONTENT_TYPE_UTF8;
        MediaType mediaType = MediaType.parse(mimeType.toString());

        if (upnpMessage.getBodyType().equals(UpnpMessage.BodyType.STRING)) {
            return RequestBody.create(upnpMessage.getBodyString(), mediaType);
        } else {
            return RequestBody.create(upnpMessage.getBodyBytes(), mediaType);
        }
    }

    @Override
    protected Callable<StreamResponseMessage> createCallable(StreamRequestMessage requestMessage, final Call call) {
        return () -> {
           // Log.d(TAG, "Sending HTTP request: " + call.request());
           // Log.v(TAG, "Body: " + requestMessage.getBodyString());
            // Use try-with-resources to ensure the Response is always closed
            try (Response response = call.execute()) {
                return createResponse(response);
            }
        };
    }

    @Override
    protected void abort(Call call) {
        // Now this works correctly, as we can cancel a Call
        if (call != null && !call.isCanceled()) {
            call.cancel();
        }
    }

    @Override
    protected boolean logExecutionException(Throwable t) {
        if (t instanceof IOException) {
            Log.w(TAG, "OkHttp execution error: " + t.getMessage());
            return true; // Suppress full stack trace for common I/O errors
        }
        return false; // Log other, unexpected exceptions
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stopping OkHttp client...");
        try {
            // Gracefully shut down the client's resources
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        } catch (Exception ex) {
            Log.e(TAG, "Error stopping OkHttp client", ex);
        }
    }

    protected StreamResponseMessage createResponse(Response response) throws IOException {
        UpnpResponse.Status status = UpnpResponse.Status.getByStatusCode(response.code());
        if (status == null) {
            throw new IOException("Received unknown HTTP status code: " + response.code());
        }

        UpnpResponse responseOperation = new UpnpResponse(response.code(), status.getStatusMsg());
        StreamResponseMessage responseMessage = new StreamResponseMessage(responseOperation);

        // Headers
        UpnpHeaders headers = new UpnpHeaders();
        headers.putAll(response.headers().toMultimap());
        responseMessage.setHeaders(headers);

        // Body
        byte[] bytes = response.body().bytes();
        if (bytes.length > 0) {
            if (responseMessage.isContentTypeMissingOrText()) {
                responseMessage.setBodyCharacters(bytes);
            } else {
                responseMessage.setBody(UpnpMessage.BodyType.BYTES, bytes);
            }
         //   Log.d(TAG, "Received response: "+ new String(bytes));
        }

       // Log.d(TAG, "Received response: " + responseOperation);
        return responseMessage;
    }
}