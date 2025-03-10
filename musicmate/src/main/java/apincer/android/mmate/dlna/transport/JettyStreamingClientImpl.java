package apincer.android.mmate.dlna.transport;

import static org.eclipse.jetty.http.HttpHeader.CONNECTION;

import android.util.Log;

import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.jupnp.http.Headers;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.message.header.UpnpHeader;
import org.jupnp.transport.spi.AbstractStreamClient;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.util.SpecificationViolationReporter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class JettyStreamingClientImpl extends AbstractStreamClient<StreamClientConfigurationImpl, Request> {
    private static final String TAG = "JettyStreamingClient";

    final protected StreamClientConfigurationImpl configuration;
    protected final HttpClient httpClient;
    protected final HttpFields.Mutable defaultHttpFields = HttpFields.build();

    public JettyStreamingClientImpl(StreamClientConfigurationImpl configuration) throws InitializationException {
        this.configuration = configuration;
        httpClient = new HttpClient();

        // These are some safety settings, we should never run into these timeouts as we
        // do our own expiration checking
        httpClient.setConnectTimeout((getConfiguration().getTimeoutSeconds() + 5) * 1000L);
        httpClient.setMaxConnectionsPerDestination(2);

        int cpus = Runtime.getRuntime().availableProcessors();
        int maxThreads = 5 * cpus;

        final QueuedThreadPool queuedThreadPool = createThreadPool(5, maxThreads, 60000);

        httpClient.setExecutor(queuedThreadPool);

        try {
            httpClient.start();
        } catch (final Exception e) {
            Log.e(TAG, "Failed to instantiate HTTP client", e);
            throw new InitializationException("Failed to instantiate HTTP client", e);
        }
    }

    @Override
    public StreamClientConfigurationImpl getConfiguration() {
        return configuration;
    }

    @Override
    protected Request createRequest(StreamRequestMessage requestMessage) {
        final UpnpRequest upnpRequest = requestMessage.getOperation();

      //  Log.v(TAG, "Creating HTTP request. URI: '{}' method: '{}'", upnpRequest.getURI(), upnpRequest.getMethod());
        Request request;
        switch (upnpRequest.getMethod()) {
            case GET:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
            case POST:
            case NOTIFY:
                try {
                    request = httpClient.newRequest(upnpRequest.getURI()).method(upnpRequest.getHttpMethodName());
                } catch (IllegalArgumentException e) {
                  //  logger.debug("Cannot create request because URI '{}' is invalid", upnpRequest.getURI(), e);
                    return null;
                }
                break;
            default:
                throw new RuntimeException("Unknown HTTP method: " + upnpRequest.getHttpMethodName());
        }
        switch (upnpRequest.getMethod()) {
            case POST:
            case NOTIFY:
                request.body(createBody(requestMessage));
                //request.content(createContentProvider(requestMessage));
                break;
            default:
        }

        // prepare default headers
        request.headers(h -> h.add(defaultHttpFields));

        // FIXME: what about HTTP2 ?
        if (requestMessage.getOperation().getHttpMinorVersion() == 0) {
            request.version(HttpVersion.HTTP_1_0);
        } else {
            request.version(HttpVersion.HTTP_1_1);
            // This closes the http connection immediately after the call.
            //
            // Even though jetty client is able to close connections properly,
            // it still takes ~30 seconds to do so. This may cause too many
            // connections for installations with many upnp devices.
            //request. setHeaders(). header(CONNECTION, "close");
            request.headers(h -> h.add(CONNECTION, "close"));
        }

        // Add the default user agent if not already set on the message
        if (!requestMessage.getHeaders().containsKey(UpnpHeader.Type.USER_AGENT)) {
            request.agent(getConfiguration().getUserAgentValue(requestMessage.getUdaMajorVersion(),
                    requestMessage.getUdaMinorVersion()));
        }

        // Headers
        addHeaders(request, requestMessage.getHeaders());

        return request;
    }

    private Request.Content createBody(StreamRequestMessage upnpMessage) {
        if (upnpMessage.getBodyType().equals(UpnpMessage.BodyType.STRING)) {
            //  logger.trace("Preparing HTTP request entity as String");
            return new StringRequestContent(upnpMessage.getBodyString(), upnpMessage.getContentTypeCharset());
        } else {
            //   logger.trace("Preparing HTTP request entity as byte[]");
            return new BytesRequestContent(upnpMessage.getBodyBytes());
        }
    }

    private void addHeaders(Request request, UpnpHeaders headers) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (final String value : entry.getValue()) {
                request.headers(h-> h.add(entry.getKey(), value));
            }
        }
    }

    @Override
    protected Callable<StreamResponseMessage> createCallable(StreamRequestMessage requestMessage, Request request) {
        return () -> {
            //logger.trace("Sending HTTP request: {}", requestMessage);
            final ContentResponse httpResponse = request.send();

            //  logger.trace("Received HTTP response: {}", httpResponse.getReason());

            // Status
            final UpnpResponse responseOperation = new UpnpResponse(httpResponse.getStatus(),
                    httpResponse.getReason());

            // Message
            final StreamResponseMessage responseMessage = new StreamResponseMessage(responseOperation);

            // Headers
            responseMessage.setHeaders(new UpnpHeaders(readHeaders(httpResponse)));

            // Body
            final byte[] bytes = httpResponse.getContent();
            if (bytes == null || 0 == bytes.length) {
               // logger.trace("HTTP response message has no entity");

                return responseMessage;
            }

           /* if (responseMessage.isContentTypeMissingOrText()) {
                Log.v(TAG, "HTTP response message contains text entity");
            } else {
                Log.v(TAG, "HTTP response message contains binary entity");
            } */

            responseMessage.setBodyCharacters(bytes);

            return responseMessage;
        };
    }

    private Headers readHeaders(ContentResponse response) {
        final Headers headers = new Headers();
        for (HttpField httpField : response.getHeaders()) {
            headers.add(httpField.getName(), httpField.getValue());
        }

        return headers;
    }

    @Override
    protected void abort(Request request) {
        request.abort(new Exception("Request aborted by API"));
    }

    @Override
    protected boolean logExecutionException(Throwable t) {
        if (t instanceof IllegalStateException) {
            // TODO: Document when/why this happens and why we can ignore it, violating the
            // logging rules of the StreamClient#sendRequest() method
           // logger.trace("Illegal state: {}", t.getMessage());
            return true;
        } else if (t.getMessage().contains("HTTP protocol violation")) {
            SpecificationViolationReporter.report(t.getMessage());
            return true;
        }
        return false;
    }

    @Override
    public void stop() {
      //  logger.trace("Shutting down HTTP client connection manager/pool");
        try {
            httpClient.stop();
        } catch (Exception ignored) {
           // logger.info("Shutting down of HTTP client throwed exception", e);
        }
    }

    private QueuedThreadPool createThreadPool(int minThreads, int maxThreads,
                                              int keepAliveTimeout) {
        QueuedThreadPool queuedThreadPool = new QueuedThreadPool(maxThreads, minThreads, keepAliveTimeout);
        queuedThreadPool.setName("jupnp-jetty-client");
        queuedThreadPool.setDaemon(true);
        return queuedThreadPool;
    }
}