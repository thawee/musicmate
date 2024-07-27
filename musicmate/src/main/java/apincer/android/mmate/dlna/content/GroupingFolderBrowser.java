package apincer.android.mmate.dlna.content;

import android.annotation.SuppressLint;
import android.content.Context;

import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.jupnp.support.model.item.MusicTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicTag;

public class GroupingFolderBrowser extends ContentBrowser {
    private static final String TAG = "GroupingFolderBrowser";
    public GroupingFolderBrowser(Context context) {
        super(context);
    }

    @Override
    public DIDLObject browseMeta(ContentDirectory contentDirectory,
                                 String myId, long firstResult, long maxResults, SortCriterion[] orderby) {
        return new StorageFolder(myId, ContentDirectoryIDs.MUSIC_GROUPING_FOLDER.getId(), myId, "mmate", getSize(
                contentDirectory, myId), null);
    }

    private Integer getSize(ContentDirectory contentDirectory, String myId) {
        String name = myId.substring(ContentDirectoryIDs.MUSIC_GROUPING_PREFIX.getId().length());
        if("_EMPTY".equalsIgnoreCase(name) ||
                "_NULL".equalsIgnoreCase(name)) { // ||
               // "None".equalsIgnoreCase(name)) {
            name = "";
        }
        String downloadName = getContext().getString(R.string.downloaded);
        if(downloadName.equals(name)) {
            return MusixMateApp.getInstance().getOrmLite().findMyIncomingSongs().size();
        }else {
            return MusixMateApp.getInstance().getOrmLite().findByGrouping(name).size();
        }
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
        String name = myId.substring(ContentDirectoryIDs.MUSIC_GROUPING_PREFIX.getId().length());
        if("_EMPTY".equalsIgnoreCase(name) ||
                "_NULL".equalsIgnoreCase(name) ) { // ||
              //  "None".equalsIgnoreCase(name)) {
            name = null;
        }
        List<MusicTag> tags;
        String downloadName = getContext().getString(R.string.downloaded);
        if(downloadName.equals(name)) {
            tags = MusixMateApp.getInstance().getOrmLite().findMyIncomingSongs();
        }else {
            tags = MusixMateApp.getInstance().getOrmLite().findByGrouping(name);
        }
        int currentCount = 0;
        for(MusicTag tag: tags) {
            if ((currentCount >= firstResult) && currentCount < (firstResult+maxResults)){
                MusicTrack musicTrack = toMusicTrack(contentDirectory, tag, myId, ContentDirectoryIDs.MUSIC_GROUPING_ITEM_PREFIX.getId());
               /* long id = tag.getId();
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
                        ContentDirectoryIDs.MUSIC_GROUPING_ITEM_PREFIX.getId()
                                + id, myId,
                        title, "", tag.getAlbum(), tag.getArtist(), resource);
                musicTrack.replaceFirstProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(
                        albumArtUri));
                musicTrack.setArtists(new PersonWithRole[]{new PersonWithRole(tag.getArtist(), "AlbumArtist")});
                resource.setBitrate(tag.getAudioBitRate());
                musicTrack.setGenres(tag.getGenre().split(",", -1));
                //musicTrack.setOriginalTrackNumber(tag.getTrack()); */
                result.add(musicTrack);
            }
            currentCount++;
        }
        result.sort(Comparator.comparing(DIDLObject::getTitle));
        return result;
    }
}