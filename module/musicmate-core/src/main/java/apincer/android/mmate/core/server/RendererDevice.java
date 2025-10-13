package apincer.android.mmate.core.server;

public class RendererDevice {
    private String udn;
    private String host;
    private String description;

    public void setDescription(String description) {
        this.description = description;
    }

    private String friendlyName;

    public RendererDevice(String udn, String host, String name) {
        this.udn = udn;
        this.host = host;
        this.friendlyName = name;
    }

    public String getHost() {
        return host;
    }

    public String getUdn() {
        return udn;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getDescription() {
        return description;
    }
}
