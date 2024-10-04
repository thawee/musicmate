package apincer.android.mmate.dlna;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.preference.PreferenceManager;

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
import java.util.List;
import java.util.UUID;

import apincer.android.mmate.Constants;
import apincer.android.mmate.dlna.content.ContentDirectory;
import apincer.android.mmate.dlna.transport.HCContentServer;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.StringUtils;

public class MediaServerDevice extends LocalDevice {
    public static final int LOCK_TIMEOUT = 5000;

    private final Context context;
    private final DeviceDetailsProvider deviceDetailsProvider;
    private final ContentDirectory contentDirectoryService;
    private final ConnectionManagerService connectionManagerService;
  //  private final UmsMediaReceiverRegistrarService mediaReceiverRegistrarService;
    public MediaServerDevice(Context context) throws ValidationException {
        super(
                new DeviceIdentity(new UDN(getUuid(context))),
                new UDADeviceType("MediaServer"),
                null,
                createDeviceIcons(context),
                createMediaServerServices(context),
                null
        );
        this.context = context;
        this.deviceDetailsProvider = new MediaDeviceDetailsProvider(context);
        this.contentDirectoryService = getServiceImplementation(ContentDirectory.class);
        this.connectionManagerService = getServiceImplementation(ConnectionManagerService.class);
    //    this.mediaReceiverRegistrarService = getServiceImplementation(UmsMediaReceiverRegistrarService.class);
    }

    @Override
    public DeviceDetails getDetails(RemoteClientInfo info) {
        if (deviceDetailsProvider != null) {
            return deviceDetailsProvider.provide(info);
        }
        return this.getDetails();
    }

    private <T> T getServiceImplementation(Class<T> baseClass) {
        for (LocalService service : getServices()) {
            if (service != null && service.getManager().getImplementation().getClass().equals(baseClass)) {
                return (T) service.getManager().getImplementation();
            }
        }
        return null;
    }

    private static LocalService<?>[] createMediaServerServices(Context context) {
        List<LocalService<?>> services = new ArrayList<>();
        services.add(createServerConnectionManagerService());
        services.add(createContentDirectoryService(context));
       // services.add(createMediaReceiverRegistrarService());
        return services.toArray(new LocalService[]{});
    }


    private static LocalService<ConnectionManagerService>  createServerConnectionManagerService() {
        LocalService<ConnectionManagerService> service = new AnnotationLocalServiceBinder().read(ConnectionManagerService.class);
        final ProtocolInfos sourceProtocols = getSourceProtocolInfos();

        service.setManager(new DefaultServiceManager<>(service, ConnectionManagerService.class) {

            @Override
            protected int getLockTimeoutMillis() {
                return 1000; //LOCK_TIMEOUT;
            }

            @Override
            protected ConnectionManagerService createServiceInstance() {
                return new ConnectionManagerService(sourceProtocols, new ProtocolInfos());
            }
        });

        return service;
    }


    private static ProtocolInfos getSourceProtocolInfos() {
        return new ProtocolInfos(
                //this one overlap all ???
                // new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, MimeType.WILDCARD, ProtocolInfo.WILDCARD),
                //this one overlap all images ???
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/" + MimeType.WILDCARD, ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio", ProtocolInfo.WILDCARD),
                //this one overlap all audio ???
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/" + MimeType.WILDCARD, ProtocolInfo.WILDCARD),

                //IMAGE
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_TN"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_SM"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_MED"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_LRG"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/jpeg", "DLNA.ORG_PN=JPEG_RES_H_V"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/png", "DLNA.ORG_PN=PNG_TN"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/png", "DLNA.ORG_PN=PNG_LRG"),
                // new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "image/gif", "DLNA.ORG_PN=GIF_LRG"),

                //AUDIO
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mpeg", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mpeg", "DLNA.ORG_PN=MP3"),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/wav", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/wave", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-wav", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/flac", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-flac", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-aiff", ProtocolInfo.WILDCARD),
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-mp4", ProtocolInfo.WILDCARD), // alac
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/x-m4a", ProtocolInfo.WILDCARD), // aac
                new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mp4", "DLNA.ORG_PN=AAC_ISO") // aac
                //  new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/L16;rate=44100;channels=2", "DLNA.ORG_PN=LPCM")
                // new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/L16", "DLNA.ORG_PN=LPCM"),
                //  new ProtocolInfo("http-get:*:audio/aac:*"), // added by thawee
                // new ProtocolInfo("http-get:*:audio/mpeg:*"),
                //  new ProtocolInfo("http-get:*:audio/x-mpegurl:*"),
                //  new ProtocolInfo("http-get:*:audio/x-wav:*"),
                // new ProtocolInfo("http-get:*:audio/mpeg:DLNA.ORG_PN=MP3"),
                //  new ProtocolInfo("http-get:*:audio/mp4:DLNA.ORG_PN=AAC_ISO"),
                // new ProtocolInfo("http-get:*:audio/x-flac:*"),
                //   new ProtocolInfo("http-get:*:audio/x-aiff:*"),
                // new ProtocolInfo("http-get:*:audio/x-ogg:*"),
                // new ProtocolInfo("http-get:*:audio/wav:*"),
                //  new ProtocolInfo("http-get:*:audio/wave:*"),
                // new ProtocolInfo("http-get:*:audio/x-ape:*"),
                //  new ProtocolInfo("http-get:*:audio/x-m4a:*"),
                // new ProtocolInfo("http-get:*:audio/x-mp4:*"), // added by thawee
                // new ProtocolInfo("http-get:*:audio/x-m4b:*"),
                //  new ProtocolInfo("http-get:*:audio/basic:*"),
                //  new ProtocolInfo("http-get:*:audio/L16;rate=11025;channels=2:DLNA.ORG_PN=LPCM"),
                //  new ProtocolInfo("http-get:*:audio/L16;rate=22050;channels=2:DLNA.ORG_PN=LPCM"),
                // new ProtocolInfo("http-get:*:audio/L16;rate=44100;channels=2:DLNA.ORG_PN=LPCM"),
                //  new ProtocolInfo("http-get:*:audio/L16;rate=48000;channels=2:DLNA.ORG_PN=LPCM"),
                //  new ProtocolInfo("http-get:*:audio/L16;rate=88200;channels=2:DLNA.ORG_PN=LPCM"),
                //  new ProtocolInfo("http-get:*:audio/L16;rate=96000;channels=2:DLNA.ORG_PN=LPCM"),
                //  new ProtocolInfo("http-get:*:audio/L16;rate=192000;channels=2:DLNA.ORG_PN=LPCM"));
                // new ProtocolInfo(Protocol.HTTP_GET, ProtocolInfo.WILDCARD, "audio/mpeg", "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01"),
                // new ProtocolInfo("http-get:*:audio/mpeg:DLNA.ORG_PN=MP3"),
                // new ProtocolInfo("http-get:*:audio/mpeg:DLNA.ORG_PN=MP3X"),
                // new ProtocolInfo("http-get:*:image/jpeg:*"),
                // new ProtocolInfo("http-get:*:image/png:*"),
                // new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG"),
                // new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_MED"),
                //  new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_SM"),
                // new ProtocolInfo("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN"));
        );
    }

    private static LocalService<ContentDirectory> createContentDirectoryService(Context context) {
        LocalService<ContentDirectory> contentDirectoryService = new AnnotationLocalServiceBinder().read(ContentDirectory.class);
        contentDirectoryService.setManager(new DefaultServiceManager<>(contentDirectoryService, null) {

           // @Override
          //  protected int getLockTimeoutMillis() {
          //      return LOCK_TIMEOUT;
          //  }

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
                return new ContentDirectory(context);
            }
        });
        return contentDirectoryService;
    }

    public static MediaServerDevice createMediaServerDevice(Context context) {
        try {
            return new MediaServerDevice(context);
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

    private static class MediaDeviceDetailsProvider implements DeviceDetailsProvider {
        private final Context context;
        private static final String MANUFACTURER_NAME = "Thawee";
        private static final String MANUFACTURER_URL = "https://github.com/thawee/musicmate";
        private static final ManufacturerDetails MANUFACTURER_DETAILS = new ManufacturerDetails(MANUFACTURER_NAME, MANUFACTURER_URL);
        private	static final DLNADoc[] DLNA_DOCS = new DLNADoc[] {new DLNADoc("DMS", DLNADoc.Version.V1_5), new DLNADoc("M-DMS", DLNADoc.Version.V1_5)};
        private	static final DLNACaps DLNA_CAPS = new DLNACaps(new String[] {});
        private static final DLNACaps SEC_CAP = new DLNACaps(new String[] {"smi", "DCM10", "getMediaInfo.sec", "getCaptionInfo.sec"});

        private MediaDeviceDetailsProvider(Context context) {
            this.context = context;
        }

        @Override
        public DeviceDetails provide(RemoteClientInfo info) {
                String modelNumber = ApplicationUtils.getVersionNumber(context);
                String modelName = "MusicMate Server";
                String modelDescription = "DLNA (UPnP/AV 1.0) Compliant Media Server - "+ApplicationUtils.getDeviceDetails();
                String friendlyName = "MusicMate Server ["+ApplicationUtils.getDeviceModel()+"]";
              //  ManufacturerDetails manufacturerDetails = new ManufacturerDetails("Thawee",
              //          "https://github.com/thawee/musicmate");
                ModelDetails modelDetails = new ModelDetails(modelName,
                        modelDescription,
                        modelNumber);
                URI presentationURI = null;
                if (!StringUtils.isEmpty(MediaServerSession.streamServerHost)) {
                    String webInterfaceUrl = "http://" + MediaServerSession.streamServerHost + ":" + HCContentServer.SERVER_PORT +"/musicmate.html";
                    presentationURI = URI.create(webInterfaceUrl);
                }
                return new DeviceDetails(
                        null,
                        friendlyName,
                        MANUFACTURER_DETAILS,
                        modelDetails,
                        null,
                        null,
                        presentationURI,
                        DLNA_DOCS,
                        DLNA_CAPS,
                        SEC_CAP);
        }
    }

}
