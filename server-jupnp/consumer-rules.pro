# jUPnP's Android JAR already keeps service annotations and annotated service members.
# AbstractDatatype.getValueType() reads the concrete datatype's generic superclass
# as ParameterizedType. Keep the type signatures and hierarchy intact in R8 full
# mode, including concrete types whose members remain eligible for shrinking.
-keepattributes Signature
-keep,allowobfuscation class org.jupnp.model.types.AbstractDatatype
-keep,allowobfuscation class org.jupnp.model.types.* extends org.jupnp.model.types.AbstractDatatype

# CSV.getBuiltinDatatype() walks generic superclasses through CSV<T> to
# ArrayList<T>. Keep both the intermediate and concrete classes/signatures.
-keep,allowobfuscation class org.jupnp.model.types.csv.CSV
-keep,allowobfuscation class org.jupnp.model.types.csv.CSVString
-keep,allowobfuscation class org.jupnp.model.types.csv.CSVUnsignedIntegerFourBytes

# AnnotationLocalServiceBinder additionally validates these ConnectionManager types
# with Class.getConstructor(String.class); MethodActionExecutor uses the same lookup.
# Keep their classes concrete for the subsequent Constructor.newInstance() call.
-keep,allowobfuscation class org.jupnp.support.model.ProtocolInfo
-keep,allowobfuscation class org.jupnp.support.model.ProtocolInfos
-keep,allowobfuscation class org.jupnp.model.ServiceReference

-keepclassmembers class org.jupnp.support.model.ProtocolInfo {
    public <init>(java.lang.String);
}
-keepclassmembers class org.jupnp.support.model.ProtocolInfos {
    public <init>(java.lang.String);
}
-keepclassmembers class org.jupnp.model.ServiceReference {
    public <init>(java.lang.String);
}

# This subclass inherits @UpnpService, so the upstream rule for directly
# annotated classes misses the constructor DefaultServiceManager invokes.
-keepclassmembers class apincer.music.server.jupnp.MediaReceiverRegistrarService {
    public <init>();
}

# @UpnpOutputArgument names getters on result objects, not on annotated services.
# AnnotationActionBinder finds these by name while binding Browse/Search and
# GetCurrentConnectionInfo, even before those actions are invoked.
-keepclassmembers class org.jupnp.support.model.BrowseResult {
    public java.lang.String getResult();
    public org.jupnp.model.types.UnsignedIntegerFourBytes getCount();
    public org.jupnp.model.types.UnsignedIntegerFourBytes getTotalMatches();
    public org.jupnp.model.types.UnsignedIntegerFourBytes getContainerUpdateID();
}
-keepclassmembers class org.jupnp.support.model.ConnectionInfo {
    public int getRcsID();
    public int getAvTransportID();
    public org.jupnp.support.model.ProtocolInfo getProtocolInfo();
    public org.jupnp.model.ServiceReference getPeerConnectionManager();
    public int getPeerConnectionID();
    public org.jupnp.support.model.ConnectionInfo$Direction getDirection();
    public org.jupnp.support.model.ConnectionInfo$Status getConnectionStatus();
}

# AnnotationStateVariableBinder calls Class.getEnumConstants() for allowedValuesEnum.
# Retain the enum constants and values() that implement that reflective lookup.
-keepclassmembers enum org.jupnp.support.model.BrowseFlag {
    public static <fields>;
    public static **[] values();
}
-keepclassmembers enum org.jupnp.support.model.ConnectionInfo$Direction {
    public static <fields>;
    public static **[] values();
}
-keepclassmembers enum org.jupnp.support.model.ConnectionInfo$Status {
    public static <fields>;
    public static **[] values();
}
-keepclassmembers enum apincer.music.server.jupnp.content.TransferStatus {
    public static <fields>;
    public static **[] values();
}
