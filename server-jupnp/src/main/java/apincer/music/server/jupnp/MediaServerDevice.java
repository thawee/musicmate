package apincer.music.server.jupnp;

import static apincer.music.core.server.BaseServer.CONTENT_SERVER_PORT;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.jupnp.binding.annotations.AnnotationLocalServiceBinder;
import org.jupnp.model.DefaultServiceManager;
import org.jupnp.model.ValidationException;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.DeviceIdentity;
import org.jupnp.model.meta.Icon;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.LocalService;
import org.jupnp.model.meta.ManufacturerDetails;
import org.jupnp.model.meta.ModelDetails;
import org.jupnp.model.profile.DeviceDetailsProvider;
import org.jupnp.model.profile.RemoteClientInfo;
import org.jupnp.model.types.DLNACaps;
import org.jupnp.model.types.DLNADoc;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDN;
import org.jupnp.support.connectionmanager.ConnectionManagerService;
import org.jupnp.support.model.Protocol;
import org.jupnp.support.model.ProtocolInfo;
import org.jupnp.support.model.ProtocolInfos;
import org.jupnp.util.MimeType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import apincer.music.core.Constants;
import apincer.music.core.server.BaseServer;
import apincer.music.server.jupnp.content.ContentDirectory;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.utils.ApplicationUtils;

public class MediaServerDevice extends LocalDevice {
    private final DeviceDetailsProvider deviceDetailsProvider;

    public MediaServerDevice(Context context, TagRepository tagRepos) throws ValidationException {
        super(
                new DeviceIdentity(new UDN(getUuid(context))),
                new UDADeviceType("MediaServer"),
                createDetails(context),
                createDeviceIcons(context),
                createMediaServerServices(context, tagRepos),
                null
        );
      //  this.tagRepos = tagRepos;
        deviceDetailsProvider = new MediaDeviceDetailsProvider(context);
        ContentDirectory contentDirectoryService = getServiceImplementation(ContentDirectory.class);
        ConnectionManagerService connectionManagerService = getServiceImplementation(ConnectionManagerService.class);
       // this.mediaReceiverRegistrarService = getServiceImplementation(UmsMediaReceiverRegistrarService.class);
    }

    private static DeviceDetails createDetails(Context context) {
        MediaDeviceDetailsProvider deviceDetailsProvider = new MediaDeviceDetailsProvider(context);
        return deviceDetailsProvider.provide(null);
    }

    @Override
    public DeviceDetails getDetails(RemoteClientInfo info) {
        if (deviceDetailsProvider != null) {
            return deviceDetailsProvider.provide(info);
        }
        return this.getDetails();
    }

    private <T> T getServiceImplementation(Class<T> baseClass) {
        return Arrays.stream(getServices()).filter(service -> service != null && service.getManager().getImplementation().getClass().equals(baseClass)).findFirst().map(service -> (T) service.getManager().getImplementation()).orElse(null);
    }

    private static LocalService<?>[] createMediaServerServices(Context context, TagRepository tagRepos) {
        List<LocalService<?>> services = new ArrayList<>();
        services.add(createServerConnectionManagerService());
        services.add(createContentDirectoryService(context, tagRepos));
       // services.add(createMediaReceiverRegistrarService());
        return services.toArray(new LocalService[]{});
    }


    private static LocalService<ConnectionManagerService>  createServerConnectionManagerService() {
        LocalService<ConnectionManagerService> service = new AnnotationLocalServiceBinder().read(ConnectionManagerService.class);
        final ProtocolInfos sourceProtocols = getSourceProtocolInfos();

        service.setManager(new DefaultServiceManager<>(service, ConnectionManagerService.class) {

            @Override
            protected int getLockTimeoutMillis() {
                return 1500; // Slightly increased from 1000 for better reliability
            }

            @Override
            protected ConnectionManagerService createServiceInstance() {
                ConnectionManagerService connectionManager = new ConnectionManagerService(sourceProtocols, new ProtocolInfos());
                // Customize connection manager for better streaming
                //connectionManager.(50);  // Increased connections for multiple clients
                return connectionManager;
            }
        });

        return service;
    }


    private static ProtocolInfos getSourceProtocolInfos() {
        return new ProtocolInfos(
                // Wildcard entries for discovery
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/" + MimeType.WILDCARD, ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/" + MimeType.WILDCARD, ProtocolInfo.WILDCARD),

                // Image formats with DLNA parameters for thumbnails
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_TN;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=00f00000000000000000000000000000"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_SM;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=00f00000000000000000000000000000"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_MED;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=00f00000000000000000000000000000"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_LRG;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=00f00000000000000000000000000000"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/png", "DLNA.ORG_PN=PNG_TN;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=00f00000000000000000000000000000"),

                // Audio formats with DLNA parameters for seeking and streaming
                // MP3 - very widely compatible
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mpeg", "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"),

                // FLAC - critical for RoPieeeXL hi-res playback
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/flac", "DLNA.ORG_PN=FLAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-flac", "DLNA.ORG_PN=FLAC;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"),

                // WAV - for uncompressed audio
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/wav", "DLNA.ORG_PN=WAV;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-wav", "DLNA.ORG_PN=WAV;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"),

                // AIFF - for studio quality audio
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-aiff", "DLNA.ORG_PN=AIFF;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"),

                // ALAC (Apple Lossless) and AAC
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-mp4", "DLNA.ORG_PN=AAC_ISO_320;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mp4", "DLNA.ORG_PN=AAC_ISO;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-m4a", "DLNA.ORG_PN=AAC_ISO;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"),

                // PCM/LPCM - for direct renderer interface
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/L16;rate=44100;channels=2", "DLNA.ORG_PN=LPCM;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/L16;rate=48000;channels=2", "DLNA.ORG_PN=LPCM;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
        );

    }

    private static LocalService<ContentDirectory> createContentDirectoryService(Context context, TagRepository tagRepos) {
        LocalService<ContentDirectory> contentDirectoryService = new AnnotationLocalServiceBinder().read(ContentDirectory.class);
        contentDirectoryService.setManager(new DefaultServiceManager<>(contentDirectoryService, null) {

            @Override
            protected void lock() {
                //don't lock cds.
            }

            @Override
            protected void unlock() {
                //don't lock cds.
            }

            @Override
            protected ContentDirectory createServiceInstance() {
                return new ContentDirectory(context, tagRepos);
            }
        });
        return contentDirectoryService;
    }

    public static MediaServerDevice createMediaServerDevice(Context context, TagRepository tagRepos) {
        try {
            return new MediaServerDevice(context, tagRepos);
        } catch (ValidationException e) {
            return null;
        }
    }

    private static String getUuid(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String mediaServerUuid = preferences.getString(Constants.PREF_MEDIA_SERVER_UUID_KEY, null);
        if (mediaServerUuid == null) {
            mediaServerUuid = UUID.randomUUID().toString();
            preferences.edit().putString(Constants.PREF_MEDIA_SERVER_UUID_KEY, mediaServerUuid).apply();
        }
        return mediaServerUuid;
    }

    private static Icon[] createDeviceIcons(Context context) {
        ArrayList<Icon> icons = new ArrayList<>();
        icons.add(new Icon("image/png", 64, 64, 24, "musicmate.png", getIconAsByteArray(context,"iconpng64.png")));
        icons.add(new Icon("image/png", 128, 128, 24, "musicmate128.png", getIconAsByteArray(context,"iconpng128.png")));
        return icons.toArray(new Icon[]{});
    }

    private static byte[] getIconAsByteArray(Context context, String iconFile) {
        try {
            InputStream in = ApplicationUtils.getAssetsAsStream(context, iconFile);
            if(in != null) {
                return IOUtils.toByteArray(in);
            }
        } catch (IOException ex) {
            Log.e("getIconAsByteArray", "cannot get icon file - "+iconFile, ex);
        }
        return null;
    }

    private record MediaDeviceDetailsProvider(Context context) implements DeviceDetailsProvider {
            private static final String MANUFACTURER_NAME = "Thawee";
            private static final String MANUFACTURER_URL = "https://github.com/thawee/musicmate";
            private static final ManufacturerDetails MANUFACTURER_DETAILS = new ManufacturerDetails(MANUFACTURER_NAME, MANUFACTURER_URL);
            private static final DLNADoc[] DLNA_DOCS = new DLNADoc[]{new DLNADoc("DMS", DLNADoc.Version.V1_5), new DLNADoc("M-DMS", DLNADoc.Version.V1_5)};
            // Replace the empty DLNA_CAPS with these capabilities
            private static final DLNACaps DLNA_CAPS = new DLNACaps(new String[]{
                    "image-upload", "audio-upload", "connection-stalling", "time-seek",
                    "range", "limited-operations", "rw", "search"
            });
            private static final List<String> CAPS_SORT = List.of("dc:title", "upnp:artist", "upnp:album", "upnp:genre");
            private static final DLNACaps SEC_CAP = new DLNACaps(new String[]{"smi", "DCM10", "getMediaInfo.sec", "getCaptionInfo.sec"});

            @Override
            public DeviceDetails provide(RemoteClientInfo info) {
                String modelNumber = ApplicationUtils.getVersionNumber(context);
                String modelName = "MusicMate Server";
                String modelDescription = "DLNA (UPnP/AV 1.0) Server - " + ApplicationUtils.getDeviceDetails();

                // For better display in mConnectHD, include the device model in brackets
                String friendlyName = "MusicMate Server [" + ApplicationUtils.getDeviceModel() + "]";

                // Add serialNumber for better device identification
                String serialNumber = getUuid(context).substring(0, 12).toUpperCase();

                ModelDetails modelDetails = new ModelDetails(modelName,
                        modelDescription,
                        modelNumber,
                        "https://github.com/thawee/musicmate"  // Add modelURL for more information
                );

               // URI presentationURI = null;
            //if (!StringUtils.isEmpty(BaseServer.getIpAddress())) {
                String webInterfaceUrl = "http://" + BaseServer.getIpAddress() + ":" + CONTENT_SERVER_PORT + "/index.html";
                URI presentationURI = URI.create(webInterfaceUrl);
           // }
            return new DeviceDetails(
                    null,
                    friendlyName,
                    MANUFACTURER_DETAILS,
                    modelDetails,
                    serialNumber,
                    null,
                    presentationURI,
                    DLNA_DOCS,
                    DLNA_CAPS,
                    SEC_CAP);
            }
        }

}
