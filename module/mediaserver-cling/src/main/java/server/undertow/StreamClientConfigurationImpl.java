package apincer.android.mmate.server.undertow;

import org.fourthline.cling.transport.spi.AbstractStreamClientConfiguration;
import org.xnio.XnioWorker;

import java.util.concurrent.ExecutorService;

public class StreamClientConfigurationImpl extends AbstractStreamClientConfiguration {
    private boolean usePersistentConnections = false;
    public StreamClientConfigurationImpl(XnioWorker timeoutExecutorService) {
        super(timeoutExecutorService);
    }

    public StreamClientConfigurationImpl(XnioWorker timeoutExecutorService, int timeoutSeconds) {
        super(timeoutExecutorService, timeoutSeconds);
    }
    public StreamClientConfigurationImpl(ExecutorService timeoutExecutorService, int timeoutSeconds) {
        super(timeoutExecutorService, timeoutSeconds);
    }

    /**
     * Defaults to <code>false</code>, avoiding obscure bugs in the JDK.
     */
    public boolean isUsePersistentConnections() {
        return usePersistentConnections;
    }

    public void setUsePersistentConnections(boolean usePersistentConnections) {
        this.usePersistentConnections = usePersistentConnections;
    }

    @Override
    public XnioWorker getRequestExecutorService() {
        return (XnioWorker)super.getRequestExecutorService();
    }
    @Override
    public void setRequestExecutorService(ExecutorService requestExecutorService) {
        if (!(requestExecutorService instanceof XnioWorker))
            throw new IllegalArgumentException();
        this.requestExecutorService = requestExecutorService;
    }
    public void setRequestExecutorService(XnioWorker requestExecutorService) {
        this.requestExecutorService = requestExecutorService;
    }
}