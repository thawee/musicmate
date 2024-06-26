package apincer.android.mmate.dlna.content;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import org.apache.commons.codec.digest.DigestUtils;
import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.PersonWithRole;
import org.jupnp.support.model.Res;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.MusicTrack;
import org.jupnp.util.MimeType;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.dlna.MediaServerService;
import apincer.android.mmate.dlna.ContentDirectory;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.utils.FileUtils;

public class MusicDownloadedFolderBrowser extends ContentBrowser {
    private static final String TAG = "MusicDownloadedFolderBrowser";
    public MusicDownloadedFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(myId, ContentDirectoryIDs.MUSIC_DOWNLOADED_TITLES_FOLDER.getId(), getContext().getString(R.string.downloaded), "mmate", getSize(
                contentDirectory, myId), null);

    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        return MusixMateApp.getInstance().getOrmLite().findMyIncomingSongs().size();
    }

    @Override
    public List<Container> browseContainer(
            ContentDirectory contentDirectory, String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
            return new ArrayList<>();
    }

    @SuppressLint("Range")
    @Override
    public List<MusicTrack> browseItem(ContentDirectory contentDirectory,
                                       String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        List<MusicTrack> result = new ArrayList<>();
        List<MusicTag> tags = MusixMateApp.getInstance().getOrmLite().findMyIncomingSongs();
        int currentCount = 0;
        for(MusicTag tag: tags) {
            if ((currentCount >= firstResult) && currentCount < (firstResult+maxResults)){
                long id = tag.getId();
                String title = tag.getTitle();
                MimeType mimeType = new MimeType("audio", tag.getAudioEncoding());
                // file parameter only needed for media players which decide
                // the
                // ability of playing a file by the file extension
                String uri = getUriString(contentDirectory, tag);
                URI albumArtUri = getAlbumArtUri(contentDirectory, tag);
                Res resource = new Res(mimeType, tag.getFileSize(), uri);
                resource.setDuration(tag.getAudioDurationAsString());
                MusicTrack musicTrack = new MusicTrack(
                        ContentDirectoryIDs.MUSIC_ALL_TITLES_ITEM_PREFIX.getId()
                                + id, ContentDirectoryIDs.MUSIC_FOLDER.getId(),
                        title, "", tag.getAlbum(), tag.getArtist(), resource);
                musicTrack.replaceFirstProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                        albumArtUri));
                musicTrack.setArtists(new PersonWithRole[]{new PersonWithRole(tag.getArtist(), "AlbumArtist")});
                resource.setBitrate(tag.getAudioBitRate());
                musicTrack.setGenres(tag.getGenre().split(",", -1));
                //musicTrack.setOriginalTrackNumber(tag.getTrack());
                result.add(musicTrack);
                Log.d(TAG, "MusicTrack: " + id + " Title: "
                        + title + " Uri: " + uri);
            }
            currentCount++;
        }
        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;
    }

    private URI getAlbumArtUri(ContentDirectory contentDirectory, MusicTag tag) {
        String absPath = tag.getPath().toLowerCase();
        if(absPath.contains("/music/") && !absPath.contains("/telegram/")) {
            // if has alblum, use parent dir
            if(!StringUtils.isEmpty(tag.getAlbum())) {
                // use directory
                absPath = FileUtils.getFolderName(absPath);
            }
        }
        String uri = DigestUtils.md5Hex(absPath)+".png";

        return URI.create("http://"
                + contentDirectory.getIpAddress() + ":"
                 + MediaServerService.CONTENT_SERVER_PORT + "/album/" + uri);
    }
}