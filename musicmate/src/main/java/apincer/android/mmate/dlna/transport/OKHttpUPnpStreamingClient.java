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
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OKHttpUPnpStreamingClient extends AbstractStreamClient<StreamingClientConfigurationImpl, Request> {
    private static final String TAG = "OKHttpUPnpStreamingClient";

    final protected StreamingClientConfigurationImpl configuration;
    final private OkHttpClient client;

    public OKHttpUPnpStreamingClient(StreamingClientConfigurationImpl configuration) throws InitializationException {
        this.configuration = configuration;
        this.client = new OkHttpClient();
    }


    @Override
    public StreamingClientConfigurationImpl getConfiguration() {
        return configuration;
    }

    @Override
    protected Request createRequest(StreamRequestMessage requestMessage) {
        try {
            Request.Builder builder = new Request.Builder();

            builder.url(requestMessage.getUri().toURL())
                    .method(requestMessage.getOperation().getHttpMethodName(), null);

            if (!requestMessage.getHeaders().containsKey(UpnpHeader.Type.USER_AGENT)) {
                String value = getConfiguration().getUserAgentValue(
                        requestMessage.getUdaMajorVersion(),
                        requestMessage.getUdaMinorVersion());

                 Log.d(TAG, "Setting header '" + UpnpHeader.Type.USER_AGENT.getHttpName() + "': " + value);
                builder.addHeader(UpnpHeader.Type.USER_AGENT.getHttpName(), value);
            }
            for (Map.Entry<String, List<String>> entry : requestMessage.getHeaders().entrySet()) {
                for (String v : entry.getValue()) {
                    String headerName = entry.getKey();
                     Log.d(TAG, "Setting header '" + headerName + "': " + v);
                    builder.addHeader(headerName, v);
                }
            }

            if (requestMessage.hasBody()) {
                Log.d(TAG, "Writing textual request body: " + requestMessage);
                MimeType contentType =
                        requestMessage.getContentTypeHeader() != null
                                ? requestMessage.getContentTypeHeader().getValue()
                                : ContentTypeHeader.DEFAULT_CONTENT_TYPE_UTF8;
                String charset =
                        requestMessage.getContentTypeCharset() != null
                                ? String.valueOf(requestMessage.getContentTypeCharset())
                                : "UTF-8";
                byte[] content = requestMessage.getBodyString().getBytes(Charset.forName(charset));

                builder.method(requestMessage.getOperation().getHttpMethodName(), RequestBody.create(content));
            }

            return builder.build();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Callable<StreamResponseMessage> createCallable(StreamRequestMessage requestMessage, Request request) {
        return new Callable<StreamResponseMessage>() {
            @Override
            public StreamResponseMessage call() throws Exception {
                 Log.d(TAG, "Sending HTTP request: " + requestMessage);
                 Log.v(TAG, "Body: " + requestMessage.getBodyString());
                Response response =client.newCall(request).execute();
                return createResponse(response);
            }
        };
    }

    @Override
    protected void abort(Request request) {

    }

    @Override
    protected boolean logExecutionException(Throwable t) {
        return true;
    }

    @Override
    public void stop() {
        try {
        } catch (Exception ex) {
            Log.i(TAG, "Error stopping HTTP client: ", ex);
        }
    }

    protected StreamResponseMessage createResponse(Response response) throws IOException {
        // Status
        if (UpnpResponse.Status.getByStatusCode(response.code()) == null) {
            throw new IllegalStateException("can't create UpnpResponse.Status from http response status: " + response.code());
        }
        UpnpResponse responseOperation =
                new UpnpResponse(
                        response.code(),
                        Objects.requireNonNull(UpnpResponse.Status.getByStatusCode(response.code())).getStatusMsg()
                );
        Log.d(TAG, "Received response: " + responseOperation);
        StreamResponseMessage responseMessage = new StreamResponseMessage(responseOperation);
        // Headers
        UpnpHeaders headers = new UpnpHeaders();
        Headers responseFields = response.headers();
        responseFields.toMultimap().forEach((name, vals) -> headers.add(name, vals.get(0)));
        responseMessage.setHeaders(headers);
        // Body
        byte[] bytes = response.body().bytes();
        if (bytes != null && bytes.length > 0 && responseMessage.isContentTypeMissingOrText()) {
            Log.d(TAG, "Response contains textual entity body, converting then setting string on message");
            responseMessage.setBodyCharacters(bytes);
        } else if (bytes != null && bytes.length > 0) {
            Log.d(TAG, "Response contains binary entity body, setting bytes on message");
            responseMessage.setBody(UpnpMessage.BodyType.BYTES, bytes);
        } else {
            Log.d(TAG, "Response did not contain entity body");
        }
        Log.d(TAG, "Response message complete: " + responseMessage);
        return responseMessage;
    }

}