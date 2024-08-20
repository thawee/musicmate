package apincer.android.mmate.dlna.content;

public enum ContentDirectoryIDs {
    PARENT_OF_ROOT("-1"),
   // ROOT("0"),
   /* IMAGES_FOLDER("100999"),
    IMAGES_BY_BUCKET_NAMES_FOLDER("200999"),
    IMAGES_BY_BUCKET_NAME_PREFIX("210999"),
    IMAGE_BY_BUCKET_PREFIX("220999"),
    IMAGES_ALL_FOLDER("300999"),
    IMAGE_ALL_PREFIX("310999") */
    MUSIC_FOLDER("0"),
    MUSIC_SOURCE_FOLDER("100999"),
    MUSIC_SOURCE_PREFIX("110999"),
    MUSIC_SOURCE_ITEM_PREFIX("120999"),
    MUSIC_COLLECTION_FOLDER("200999"),
    MUSIC_COLLECTION_PREFIX("210999"),
    MUSIC_COLLECTION_ITEM_PREFIX("220999"),
    MUSIC_RESOLUTION_FOLDER("300999"),
    MUSIC_RESOLUTION_PREFIX("310999"),
    MUSIC_RESOLUTION_ITEM_PREFIX("320999"),
   // MUSIC_ALL_TITLES_FOLDER("600999"),
   // MUSIC_ALL_TITLES_ITEM_PREFIX("610999"),
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
    MUSIC_GROUPING_PREFIX("510999"),
    MUSIC_GROUPING_ITEM_PREFIX("520999");
   // MUSIC_DOWNLOADED_TITLES_FOLDER("400999"),
  //  MUSIC_DOWNLOADED_TITLES_ITEM_PREFIX("410999");

    final String id;

    ContentDirectoryIDs(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

}