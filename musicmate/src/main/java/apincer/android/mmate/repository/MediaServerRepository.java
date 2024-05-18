package apincer.android.mmate.repository;

import java.util.List;

import apincer.android.mmate.MusixMateApp;

public class MediaServerRepository {
    public static List<MediaServer> getAllServers() {
        return MusixMateApp.getInstance().getOrmLite().getMediaServers();
       /* List list = new ArrayList();
        MediaServer server = new MediaServer();
        server.setIp("10.100.1.198");
        server.setPort(22);
        server.setName("tc@pcp.local");
        server.setUsername("tc");
        server.setPassword("piCore");
        server.setPath("/mnt/mmcblk0p2/media");
        list.add(server);
        return list; */
    }

    public static void saveServers(List<MediaServer> servers) {
        MusixMateApp.getInstance().getOrmLite().saveMediaServers(servers);
    }
}
