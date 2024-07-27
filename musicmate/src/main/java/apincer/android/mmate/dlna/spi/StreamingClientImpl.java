package apincer.android.mmate.dlna.spi;

import android.util.Log;

import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
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
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class StreamingClientImpl extends AbstractStreamClient<StreamingClientConfigurationImpl, HttpUriRequest> {
    private static final String TAG = "StreamingClientImpl";

    final protected StreamingClientConfigurationImpl configuration;
    final private CloseableHttpClient httpClient;

    public StreamingClientImpl(StreamingClientConfigurationImpl configuration) throws InitializationException {
        this.configuration = configuration;
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(Timeout.of(60, TimeUnit.SECONDS))
                .setValidateAfterInactivity(TimeValue.of(10, TimeUnit.MILLISECONDS))
                .build());
        connectionManager.setMaxTotal(10);
        httpClient = HttpClientBuilder.create().setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE).setConnectionManager(connectionManager).build();
    }

    @Override
    public StreamingClientConfigurationImpl getConfiguration() {
        return configuration;
    }

    @Override
    protected HttpUriRequest createRequest(StreamRequestMessage requestMessage) {
        return new HttpUriRequestBase(requestMessage.getOperation().getHttpMethodName(), requestMessage.getUri());
    }

    @Override
    protected Callable<StreamResponseMessage> createCallable(final StreamRequestMessage requestMessage,
                                                             final HttpUriRequest request) {
        return () -> {
           // Log.d(TAG, "Sending HTTP request: " + requestMessage);
           // Log.v(TAG, "Body: " + requestMessage.getBodyString());
            applyRequestHeader(requestMessage, request);
            applyRequestBody(requestMessage, request);
            return httpClient.execute(request, this::createResponse);

        };
    }

    @Override
    protected void abort(HttpUriRequest httpUriRequest) {
        Log.d(TAG, "Received request abort, ignoring it!! ");
        httpUriRequest.abort();
    }

    @Override
    protected boolean logExecutionException(Throwable t) {
        return true;
    }

    @Override
    public void stop() {
        try {
            httpClient.close();
        } catch (Exception ex) {
            Log.i(TAG, "Error stopping HTTP client: ", ex);
        }
    }

    private void applyRequestHeader(StreamRequestMessage requestMessage, ClassicHttpRequest request) {
        if (!requestMessage.getHeaders().containsKey(UpnpHeader.Type.USER_AGENT)) {
            String value = getConfiguration().getUserAgentValue(
                    requestMessage.getUdaMajorVersion(),
                    requestMessage.getUdaMinorVersion());

           // Log.d(TAG, "Setting header '" + UpnpHeader.Type.USER_AGENT.getHttpName() + "': " + value);
            request.addHeader(UpnpHeader.Type.USER_AGENT.getHttpName(), value);
        }
        for (Map.Entry<String, List<String>> entry : requestMessage.getHeaders().entrySet()) {
            for (String v : entry.getValue()) {
                String headerName = entry.getKey();
               // Log.d(TAG, "Setting header '" + headerName + "': " + v);
                request.addHeader(headerName, v);
            }
        }
    }

    private void applyRequestBody(StreamRequestMessage requestMessage, ClassicHttpRequest request) {
        // Body
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
            request.setEntity(new ByteArrayEntity(content, ContentType.parse(contentType.toString())));
        }
    }


    protected StreamResponseMessage createResponse(ClassicHttpResponse response) throws IOException {
        // Status
        if (UpnpResponse.Status.getByStatusCode(response.getCode()) == null) {
            throw new IllegalStateException("can't create UpnpResponse.Status from http response status: " + response.getCode());
        }
        UpnpResponse responseOperation =
                new UpnpResponse(
                        response.getCode(),
                        Objects.requireNonNull(UpnpResponse.Status.getByStatusCode(response.getCode())).getStatusMsg()
                );
        Log.d(TAG, "Received response: " + responseOperation);
        StreamResponseMessage responseMessage = new StreamResponseMessage(responseOperation);
        // Headers
        UpnpHeaders headers = new UpnpHeaders();
        Header[] responseFields = response.getHeaders();
        for (Header header : responseFields) {
            headers.add(header.getName(), header.getValue());
        }
        responseMessage.setHeaders(headers);
        // Body
        byte[] bytes = EntityUtils.toByteArray(response.getEntity());
        if (bytes != null && bytes.length > 0 && responseMessage.isContentTypeMissingOrText()) {
            Log.d(TAG, "Response contains textual entity body, converting then setting string on message");
            responseMessage.setBodyCharacters(bytes);
        } else if (bytes != null && bytes.length > 0) {
            Log.d(TAG, "Response contains binary entity body, setting bytes on message");
            responseMessage.setBody(UpnpMessage.BodyType.BYTES, bytes);
        } else {
            Log.d(TAG, "Response did not contain entity body");
        }
      //  Log.d(TAG, "Response message complete: " + responseMessage);
        return responseMessage;
    }

}