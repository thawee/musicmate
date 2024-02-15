package apincer.android.mmate.dlna;

import org.fourthline.cling.DefaultUpnpServiceConfiguration;


public class UpnpConfiguration extends DefaultUpnpServiceConfiguration {

    public UpnpConfiguration(int streamListenPort) {
        super(streamListenPort, false);
    }
}
