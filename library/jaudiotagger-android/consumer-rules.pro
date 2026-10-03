# AbstractID3v2Frame and ID3v22Frame resolve frame bodies by name with
# Class.forName("org.jaudiotagger.tag.id3.framebody.FrameBody" + frameId), then
# invoke the no-arg, (ByteBuffer, int) or copy constructor. Keep the names too.
-keep class org.jaudiotagger.tag.id3.framebody.FrameBody* {
    public <init>(...);
}

# ID3Tags.copyObject() calls getClass().getConstructor(getClass()) on frames,
# frame bodies and datatypes when tags are copied or converted between versions.
-keepclassmembers class org.jaudiotagger.tag.id3.** {
    public <init>(...);
}
-keepclassmembers class org.jaudiotagger.tag.datatype.** {
    public <init>(...);
}

# ChunkContainerReader registers ASF chunk readers by class and creates them
# with Class.newInstance(); most of those constructors are protected.
-keepclassmembers class * implements org.jaudiotagger.audio.asf.io.ChunkReader {
    <init>();
}
