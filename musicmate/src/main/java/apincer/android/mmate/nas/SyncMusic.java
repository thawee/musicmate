package apincer.android.mmate.nas;

import android.content.Context;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.IOException;
import java.util.List;

import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.utils.FileUtils;

public class SyncMusic {
    private final String      host = "10.100.1.198";
    private final int         port=22;
    private final String      username= "tc"; //"pi"; "tc";
    private final String      password= "piCore"; //piCore";  "raspberry";

    private final String      music_path = "/mnt/USB"; ///var/lib/mpd/music/INT"; //""/mnt/mmcblk0p3/music";
    private final String      radio_path= "/var/lib/mympd/webradios"; //""/mnt/mmcblk0p3/playlists";
    private final String      playlists_path = "/var/lib/mpd/playlists"; ///var/lib/mpd/playlists"; //""/mnt/mmcblk0p3/playlists";

    public void sync(Context context, MusicTag tag) throws IOException {
        String targetPath = FileRepository.newInstance(context).buildCollectionPath(tag, false);
       // if(targetPath.startsWith("Music/")) {
        //    targetPath = targetPath.substring(6);
       // }
        String targetDir = appendIfMissing(music_path, "/")+FileUtils.getFolderName(targetPath);
        String targetFilename = FileUtils.getFullFileName(targetPath);
        SFTPClient client = new SFTPClient();
        try {
            client.connect(host, port, username, password);
            client.mkdirs(targetDir);
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

    public static String appendIfMissing(String src, String ch) {
        if(!src.endsWith(ch)) {
            return src+ch;
        }
        return src;
    }

    public void syncPlaylist(Context context, String filename) throws IOException {
        String targetDir = appendIfMissing(playlists_path, "/");
        String targetFilename = FileUtils.getFullFileName(filename);
        SFTPClient client = new SFTPClient();
        try {
            client.connect(host, port, username, password);
            client.mkdirs(targetDir);
            client.uploadFile(filename,  targetDir,targetFilename);
        } catch (JSchException e) {
            throw new RuntimeException(e);
        } catch (SftpException e) {
            throw new RuntimeException(e);
        }
    }

    public void syncRadioPlaylist(Context context, String filename) throws IOException {
        String targetDir = appendIfMissing(radio_path, "/");
        String targetFilename = FileUtils.getFullFileName(filename);
        SFTPClient client = new SFTPClient();
        try {
            client.connect(host, port, username, password);
            client.mkdirs(targetDir);
            client.uploadFile(filename,  targetDir,targetFilename);
        } catch (JSchException e) {
            throw new RuntimeException(e);
        } catch (SftpException e) {
            throw new RuntimeException(e);
        }
    }

    public void syncRadioPlaylist(List<String> filenames) throws IOException {
        String targetDir = appendIfMissing(radio_path, "/");
        SFTPClient client = new SFTPClient();
        try {
            client.connect(host, port, username, password);
            client.mkdirs(targetDir);
            for(String filename: filenames) {
                String targetFilename = FileUtils.getFullFileName(filename);
                client.uploadFile(filename, targetDir, targetFilename);
            }
        } catch (JSchException e) {
            throw new RuntimeException(e);
        } catch (SftpException e) {
            throw new RuntimeException(e);
        }
    }
}