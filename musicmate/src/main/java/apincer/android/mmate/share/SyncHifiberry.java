package apincer.android.mmate.share;

import android.content.Context;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.IOException;

import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.utils.FileUtils;

public class SyncHifiberry {
    private final String      host = "hifiberry.local";
    private final int         port=22;
    private final String      username="root";
    private final String      password="hifiberry";

    private final String      remote_path="/data/library/music/mmate/";

    public void sync(Context context, MusicTag tag) throws IOException {
        String targetPath = FileRepository.newInstance(context).buildCollectionPath(tag, false);
        if(targetPath.startsWith("Music/")) {
            targetPath = targetPath.substring(6);
        }
        String targetDir = remote_path+ FileUtils.getFolderName(targetPath);
        String targetFilename = FileUtils.getFullFileName(targetPath);
        SFTPClient client = new SFTPClient();
        try {
            client.connect(host, port, username, password);
            client.uploadFile(tag.getPath(),  targetDir,targetFilename);
            // send image file if existed
            File cover = FileRepository.getFolderCoverArt(tag);
            if(cover != null) {
                client.uploadFile(cover.getAbsolutePath(), targetDir,cover.getName());
            }
        } catch (JSchException e) {
            throw new RuntimeException(e);
        } catch (SftpException e) {
            throw new RuntimeException(e);
        }
    }

    public void finallise() {
        try {
            SFTPClient client = new SFTPClient();
            client.connect(host, port, username, password);
            String command="nohup /opt/hifiberry/bin/update-mpd-db&";
            client.exec(command);
        } catch (JSchException e) {
            throw new RuntimeException(e);
        }
    }
}
