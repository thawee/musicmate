package apincer.android.mmate.repository;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import apincer.android.mmate.utils.StringUtils;

public class SearchCriteria implements Parcelable {
    public SearchCriteria(TYPE type) {
        this.type = type;
    }
    public SearchCriteria(TYPE type, String keyword) {
        this.type = type;
        this.keyword = keyword;
    }

   // public enum TYPE {MY_SONGS, AUDIOPHILE, SEARCH, SEARCH_BY_ARTIST,SEARCH_BY_ALBUM,GENRE,AUDIO_FORMAT,DOWNLOAD,SIMILAR_TITLE,DUPLICATE,GROUPING,AUDIO_SQ,AUDIO_SAMPLE_RATE}
    public enum TYPE {MY_SONGS, MEDIA_QUALITY, PUBLISHER, SEARCH, SEARCH_BY_ARTIST,SEARCH_BY_ALBUM,GENRE,AUDIO_FORMAT,GROUPING,AUDIO_SQ,AUDIO_SAMPLE_RATE} //,AUDIO_PCM,AUDIO_HIRES,AUDIO_HIFI}

    public enum RESULT_TYPE {ALL,TOP_RESULT,ARTIST,ALBUM,TRACKS}

    protected SearchCriteria(Parcel in) {
        String typeName = in.readString();
        keyword = in.readString();
        filterType = in.readString();
        filterText = in.readString();
        try {
            type = TYPE.valueOf(typeName);
        }catch (Exception ex) {
            type = TYPE.MY_SONGS;
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(type.name());
        dest.writeString(StringUtils.trimToEmpty(keyword));
        dest.writeString(StringUtils.trimToEmpty(filterType));
        dest.writeString(StringUtils.trimToEmpty(filterText));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SearchCriteria> CREATOR = new Creator<SearchCriteria>() {
        @Override
        public SearchCriteria createFromParcel(Parcel in) {
            return new SearchCriteria(in);
        }

        @Override
        public SearchCriteria[] newArray(int size) {
            return new SearchCriteria[size];
        }
    };

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj == null) return false;
        if(obj instanceof SearchCriteria) {
            SearchCriteria criteria = (SearchCriteria) obj;
            if(criteria.type == type && StringUtils.equals(criteria.keyword,keyword) &&
                    StringUtils.equals(criteria.getFilterText(), filterText) &&
                    StringUtils.equals(criteria.getFilterType(), filterType)) {
                return true;
            }
        }
        return super.equals(obj);
    }

    public void searchFor(String searchFor) {
        //if(type != TYPE.SEARCH || type != TYPE.SEARCH_BY_ALBUM || type != TYPE.SEARCH_BY_ARTIST) {
        if(type != TYPE.SEARCH) {
            previousType = type;
            previousKeyword = keyword;
        }
        type = TYPE.SEARCH;
        keyword = searchFor;
    }

    public void resetSearch() {
        if(type == TYPE.SEARCH) {
            type = previousType;
            keyword = previousKeyword;
        }
    }

    protected String keyword;
    protected String filterType;
    protected String filterText;

    public String getFilterType() {
        return filterType;
    }

    public void setFilterType(String filterType) {
        this.filterType = filterType;
    }

    public String getFilterText() {
        return filterText;
    }

    public void setFilterText(String filterText) {
        this.filterText = filterText;
    }

    public TYPE getType() {
        return type;
    }
    private TYPE type;
    private TYPE previousType;
    protected String previousKeyword;
}
