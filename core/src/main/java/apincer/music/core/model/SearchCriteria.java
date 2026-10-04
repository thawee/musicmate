package apincer.music.core.model;

import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import apincer.music.core.utils.StringUtils;

public class SearchCriteria implements Parcelable {
    private TYPE type;
    protected String keyword;
    protected String filterType;
    protected String filterText;
    private boolean searchMode;
    private String searchText;

    public SearchCriteria(TYPE type) {
        this.type = type;
    }

    public SearchCriteria(TYPE type, String keyword) {
        this.type = type;
        this.keyword = keyword;
    }

    protected SearchCriteria(Parcel in) {
        String typeName = in.readString();
        keyword = in.readString();
        filterType = in.readString();
        filterText = in.readString();
        try {
            type = TYPE.valueOf(typeName);
        } catch (Exception ex) {
            type = TYPE.LIBRARY;
        }
    }

    public enum TYPE {LIBRARY, MEDIA_QUALITY, PUBLISHER, GENRE, PLAYLIST, SOUND_GRADE, ARTIST}

    public boolean isSearchMode() {
        return searchMode;
    }

    public void setSearchMode(boolean searchMode) {
        this.searchMode = searchMode;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public void setType(TYPE type) {
        this.type = type;
    }

    public TYPE getType() {
        return type;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(type.name());
        dest.writeString(trimToEmpty(keyword));
        dest.writeString(trimToEmpty(filterType));
        dest.writeString(trimToEmpty(filterText));
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
        if (obj == null) return false;
        if (obj instanceof SearchCriteria) {
            SearchCriteria criteria = (SearchCriteria) obj;
            return criteria.type == type
                    && StringUtils.equals(criteria.keyword, keyword)
                    && StringUtils.equals(criteria.filterText, filterText)
                    && StringUtils.equals(criteria.filterType, filterType);
        }
        return super.equals(obj);
    }

    public void searchFor(String searchFor) {
        searchMode = true;
        searchText = trimToEmpty(searchFor);
    }

    public void resetSearch() {
        searchMode = false;
        searchText = null;
    }

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
}
