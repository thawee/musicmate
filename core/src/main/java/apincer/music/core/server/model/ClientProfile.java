package apincer.music.core.server.model;

public class ClientProfile {
    public final String name;
    public final int chunkSize;
    public final boolean keepAlive;
    public final int maxConnections;

    // Audiophile Flags
    public final boolean supportsGapless;
    public final boolean supportsHighRes;
    public final boolean supportsDirectStreaming;
    public final boolean supportsLosslessStreaming;
    public final boolean supportsBitPerfectStreaming;

    // Builder Pattern or Constructor for easy creation
    public ClientProfile(String name, int chunkSize, boolean keepAlive, int maxConnections,
                         boolean supportsGapless, boolean supportsHighRes,
                         boolean supportsDirectStreaming, boolean supportsLosslessStreaming,
                         boolean supportsBitPerfectStreaming) {
        this.name = name;
        this.chunkSize = chunkSize;
        this.keepAlive = keepAlive;
        this.maxConnections = maxConnections;
        this.supportsGapless = supportsGapless;
        this.supportsHighRes = supportsHighRes;
        this.supportsDirectStreaming = supportsDirectStreaming;
        this.supportsLosslessStreaming = supportsLosslessStreaming;
        this.supportsBitPerfectStreaming = supportsBitPerfectStreaming;
    }

    // Factory method for "Standard" clients
    public static ClientProfile standard(int bufferSize) {
        return new ClientProfile("default", bufferSize, true, 5, false, false, false, false, false);
    }
}