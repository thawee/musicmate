package apincer.android.mmate.dlna.content;

public enum ContentDirectoryIDs {
    PARENT_OF_ROOT("-1"),
   // ROOT("0"),
   /* IMAGES_FOLDER("100999"),
    IMAGES_BY_BUCKET_NAMES_FOLDER("200999"),
    IMAGES_BY_BUCKET_NAME_PREFIX("210999"),
    IMAGE_BY_BUCKET_PREFIX("220999"),
    IMAGES_ALL_FOLDER("300999"),
    IMAGE_ALL_PREFIX("310999"),
    VIDEOS_FOLDER("400999"),
    VIDEO_PREFIX("410999"), */
    //MUSIC_FOLDER("500999"),
    MUSIC_FOLDER("0"),
    MUSIC_ALL_TITLES_FOLDER("600999"),
    MUSIC_ALL_TITLES_ITEM_PREFIX("610999"),
    MUSIC_GENRES_FOLDER("700999"),
    MUSIC_GENRE_PREFIX("710999"),
    MUSIC_GENRE_ITEM_PREFIX("720999"),
    MUSIC_ALBUMS_FOLDER("800999"),
    MUSIC_ALBUM_PREFIX("810999"),
    MUSIC_ALBUM_ITEM_PREFIX("820999"),
    MUSIC_ARTISTS_FOLDER("900999"),
    MUSIC_ARTIST_PREFIX("910999"),
    MUSIC_ARTIST_ITEM_PREFIX("920999"),
    MUSIC_GROUPING_FOLDER("500999"),
    MUSIC_DOWNLOADED_TITLES_FOLDER("400999");

    final String id;

    ContentDirectoryIDs(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

}