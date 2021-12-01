package apincer.android.mmate.item;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractHeaderItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;

/**
 * This is a header item with custom layout for section headers.
 * <p><b>Note:</b> THIS ITEM IS NOT A SCROLLABLE HEADER.</p>
 * A Section should not contain others Sections and headers are not Sectionable!
 */
public class HeaderItem
        extends AbstractHeaderItem<HeaderItem.HeaderViewHolder>
        implements IFilterable<MediaFilter> {

    private String id;
    private String title;

    public SearchCriteria getSearchCriteria() {
        return searchCriteria;
    }

    public void setSearchCriteria(SearchCriteria searchCriteria) {
        this.searchCriteria = searchCriteria;
    }

    private SearchCriteria searchCriteria;

    public SearchCriteria.RESULT_TYPE getResultType() {
        return resultType;
    }

    public void setResultType(SearchCriteria.RESULT_TYPE resultType) {
        this.resultType = resultType;
    }

    private SearchCriteria.RESULT_TYPE resultType;
    private int count = 0;
    private long duration = 0;
    private HeaderViewHolder holder;
    private FlexibleAdapter adapter;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    //private String filterString = "";
    //private String filterType = "";

    List<String> groupings = new ArrayList<>();
    public HeaderItem(String title) {
        super();
        this.id = title;
        this.title = title;
        setDraggable(true);
    }

    @Override
    public boolean equals(Object inObject) {
        if (inObject instanceof HeaderItem) {
            HeaderItem inItem = (HeaderItem) inObject;
            return this.getId().equals(inItem.getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public int getSpanSize(int spanCount, int position) {
        return spanCount;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.view_list_header;
    }
/*
    private String[] parseFilter(FlexibleAdapter adapter) {
        if(adapter.hasFilter()) {
            filterString = (String) adapter.getFilter(String.class);
            //String type ="";
            if(StringUtils.startsWith(filterString, "artist:")) {
                filterString = StringUtils.formatTitle(filterString.substring("artist:".length()));
                filterType = "Artist";
            }else if(StringUtils.startsWith(filterString, "album:")) {
                filterString = StringUtils.formatTitle(filterString.substring("album:".length()));
                filterType = "Album";
            }else if(StringUtils.startsWith(filterString, "albumartist:")) {
                filterString = StringUtils.formatTitle(filterString.substring("albumartist:".length()));
                filterType = "AlbumArtist";
            }else if(StringUtils.startsWith(filterString, "genre:")) {
                filterString = StringUtils.formatTitle(filterString.substring("genre:".length()));
                filterType = "Genre";
            }else if(StringUtils.startsWith(filterString, "grouping:")) {
                filterString = StringUtils.formatTitle(filterString.substring("grouping:".length()));
                filterType = "Grouping";
            }else if(StringUtils.startsWith(filterString, "directory:")) {
                filterString = filterString.substring("directory:".length());

                if(MediaFileRepository.getInstance(null)!= null) {
                    MediaFileRepository.StorageInfo sinfo = MediaFileRepository.getInstance(null).getStorageInfo(filterString);
                    if(sinfo!=null) {
                        String spath = sinfo.path.getAbsolutePath();
                        filterString = sinfo.shortName + filterString.replace(spath, ":");
                    }
                }
                filterType = "Directory";
            }
        } else {
            filterString = "";
            filterType = "";
        }
        String []filter = new String[2];
        filter[0] = filterType;
        filter[1] = filterString;

        return filter;
    } */

    private Drawable getFilterIcon() {
        Drawable drawable = null;
        MediaFilter filter = (MediaFilter) adapter.getFilter(MediaFilter.class);
        if(filter == null) return null;

        if(filter.getType() == MediaFilter.TYPE.ARTIST) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_artist_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else if(filter.getType() == MediaFilter.TYPE.ALBUM) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_album_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else if(filter.getType() == MediaFilter.TYPE.ALBUM_ARTIST) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_album_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else if(filter.getType() == MediaFilter.TYPE.GENRE) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_style_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else if(filter.getType() == MediaFilter.TYPE.GROUPING) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_group_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else if(filter.getType() == MediaFilter.TYPE.PATH) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_folder_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_music_24dp, holder.context.getColor(R.color.drawer_header_background));
        }
        /*
        if("Artist".equalsIgnoreCase(filterType)) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_artist_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else if("Album".equalsIgnoreCase(filterType)) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_album_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else if("AlbumArtist".equalsIgnoreCase(filterType)) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_album_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else if("Genre".equalsIgnoreCase(filterType)) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_style_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else if("Grouping".equalsIgnoreCase(filterType)) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_group_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else if("Directory".equalsIgnoreCase(filterType)) {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_folder_24dp, holder.context.getColor(R.color.drawer_header_background));
        }else {
            drawable = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_music_24dp, holder.context.getColor(R.color.drawer_header_background));
        } */
        return drawable;
    }


    private @DrawableRes int getFilterIconRes() {
        MediaFilter filter = (MediaFilter) adapter.getFilter(MediaFilter.class);
        if(filter == null) return R.drawable.ic_context_music_24dp;

        if(filter.getType() == MediaFilter.TYPE.ARTIST) {
            return R.drawable.ic_context_artist_24dp;
        }else if(filter.getType() == MediaFilter.TYPE.ALBUM) {
            return R.drawable.ic_context_album_24dp;
        }else if(filter.getType() == MediaFilter.TYPE.ALBUM_ARTIST) {
            return R.drawable.ic_context_album_24dp;
        }else if(filter.getType() == MediaFilter.TYPE.GENRE) {
            return R.drawable.ic_context_style_24dp;
        }else if(filter.getType() == MediaFilter.TYPE.GROUPING) {
            return R.drawable.ic_context_group_24dp;
        }else if(filter.getType() == MediaFilter.TYPE.PATH) {
            return R.drawable.ic_context_folder_24dp;
        }else {
            return R.drawable.ic_context_music_24dp;
        }

        /*
        if("Artist".equalsIgnoreCase(filterType)) {
            return R.drawable.ic_context_artist_24dp;
        }else if("Album".equalsIgnoreCase(filterType)) {
            return R.drawable.ic_context_album_24dp;
        }else if("AlbumArtist".equalsIgnoreCase(filterType)) {
            return R.drawable.ic_context_album_24dp;
        }else if("Genre".equalsIgnoreCase(filterType)) {
            return R.drawable.ic_context_style_24dp;
        }else if("Grouping".equalsIgnoreCase(filterType)) {
            return R.drawable.ic_context_group_24dp;
        }else if("Directory".equalsIgnoreCase(filterType)) {
            return R.drawable.ic_context_folder_24dp;
        }else {
            return R.drawable.ic_context_music_24dp;
        }*/
    }

    @Override
    public HeaderViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new HeaderViewHolder(view, adapter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void bindViewHolder(FlexibleAdapter adapter, HeaderViewHolder holder, int position, List payloads) {
        this.holder = holder;
        this.adapter = adapter;
        holder.mFilterBar.setVisibility(View.GONE);
        String filterFound ="";
        if(holder.mSubtitle != null) {
            if(adapter.hasFilter()) {
                //parseFilter(adapter);
                //holder.mSubtitle.setText(filterType+" ["+filterString+"]");
                MediaFilter filter = (MediaFilter) adapter.getFilter(MediaFilter.class);
                holder.mSubtitle.setText("["+filter.getDisplayKeyword()+"]");
                holder.mSubtitle.setCompoundDrawablesWithIntrinsicBounds(getFilterIcon(),null,null,null);
                holder.mSubtitle.setTextColor(holder.context.getColor(R.color.drawer_header_background));
                holder.mSubtitle.setVisibility(View.VISIBLE);
                filterFound = StringUtils.formatSongSize(adapter.getMainItemCount())+"/";
            }else if(getResultType()== SearchCriteria.RESULT_TYPE.ALL){
                holder.mSubtitle.setCompoundDrawablesWithIntrinsicBounds(null,null,null,null);
                holder.mSubtitle.setText(StringUtils.formatDuration(getDuration(), true));
                holder.mSubtitle.setTextColor(holder.context.getColor(R.color.grey200));
                holder.mSubtitle.setVisibility(View.VISIBLE);
            }else {
                holder.mSubtitle.setVisibility(View.GONE);
            }

            for (int i = 0; i < adapter.getItemCount(); i++) {
                IFlexible fi = adapter.getItem(i);
                if (fi instanceof MediaItem) {
                    MediaItem item = (MediaItem) fi;
                    String grp = item.getTag().getGrouping();
                    grp = StringUtils.isEmpty(grp) ? SearchCriteria.DEFAULT_MUSIC_GROUPING : grp;
                    if (!groupings.contains(grp)) {
                        groupings.add(grp);
                    }
                }
            }
            Collections.sort(groupings);
        }

        holder.mTitle.setText(getTitle() + Constants.HEADER_CNT_PREFIX +filterFound+StringUtils.formatSongSize(getCount())+Constants.HEADER_CNT_SUFFIX);

       // holder.mTitle.setText(getTitle() + Constants.HEADER_CNT_PREFIX +filterFound+StringUtils.formatSongSize(getCount())+Constants.HEADER_CNT_SUFFIX);

        Drawable titleIcon = null;
        if(getSearchCriteria() !=null) {
            if (getSearchCriteria().getType() == SearchCriteria.TYPE.DOWNLOAD) {
                titleIcon = holder.context.getDrawable(R.drawable.baseline_cloud_download_white_24);
            } else if (getSearchCriteria().getType() == SearchCriteria.TYPE.SIMILAR_TITLE) {
                titleIcon = holder.context.getDrawable(R.drawable.music_similar_white_24);
            } else if (getSearchCriteria().getType() == SearchCriteria.TYPE.SIMILAR_TITLE_ARTIST) {
                titleIcon = holder.context.getDrawable(R.drawable.music_similar_white_24);
            } else if (getSearchCriteria().getType() == SearchCriteria.TYPE.SEARCH_BY_ALBUM) {
                titleIcon = holder.context.getDrawable(R.drawable.ic_album_white_24dp);
            } else if (getSearchCriteria().getType() == SearchCriteria.TYPE.SEARCH_BY_ARTIST) {
                titleIcon = holder.context.getDrawable(R.drawable.ic_artist_white_24dp);
            } else if (getSearchCriteria().getType() == SearchCriteria.TYPE.AUDIO_SQ) {
                titleIcon = holder.context.getDrawable(R.drawable.ic_graphic_white_24dp);
            }else if (getSearchCriteria().getType() == SearchCriteria.TYPE.SEARCH) {
                if(getResultType() == SearchCriteria.RESULT_TYPE.TOP_RESULT) {
                    titleIcon = holder.context.getDrawable(R.drawable.ic_top_result_white_24dp);
                }else if(getResultType() == SearchCriteria.RESULT_TYPE.ARTIST) {
                    titleIcon = holder.context.getDrawable(R.drawable.ic_artist_white_24dp);
                }else if(getResultType() == SearchCriteria.RESULT_TYPE.ALBUM) {
                    titleIcon = holder.context.getDrawable(R.drawable.ic_album_white_24dp);
                }else if(getResultType() == SearchCriteria.RESULT_TYPE.TRACKS) {
                    titleIcon = holder.context.getDrawable(R.drawable.ic_audiotrack_white_24dp);
                }
            }
        }

        if(titleIcon==null) {
            titleIcon = holder.context.getDrawable(R.drawable.ic_library_music_white_24dp);
        }
        holder.mTitle.setCompoundDrawablesWithIntrinsicBounds(titleIcon,null,null,null);
    }

    @Override
    public boolean filter(MediaFilter constraint) {
       return false;
        // return getTitle() != null && getTitle().toLowerCase().trim().contains(constraint);
    }

    public void toggleFilter() {
       // if(!holder.mFilterBar.isExpanded()) {
        MediaFilter filter = (MediaFilter) adapter.getFilter(MediaFilter.class);
            if(holder.mFilterBar.getVisibility() == View.GONE) {
                holder.mFilters.removeAllViews();
                holder.mFilterBar.setVisibility(View.VISIBLE);
                holder.mFilters.setClickable(true);
 
                if(adapter.hasFilter() && filter != null) {
                    if(filter.getType() != MediaFilter.TYPE.GROUPING) {
                        View view = LayoutInflater.from(holder.context).inflate(R.layout.view_item_filter_chip, null);

                        Chip chip = view.findViewById(R.id.chip);
                        chip.setText(filter.getKeyword());

                        Drawable icon = UIUtils.getTintedDrawable(holder.context,getFilterIconRes(), holder.context.getColor(R.color.selected));
                        chip.setChipIcon(icon);
                        chip.setCloseIconVisible(true);
                        chip.setTextColor(holder.context.getColor(R.color.selected));
                        chip.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                adapter.setFilter(null);
                                adapter.filterItems();
                                toggleFilter();
                            }
                        });
                        holder.mFilters.addView(chip);
                    }
                }

                for (String grouping : groupings) {
                  View view = LayoutInflater.from(holder.context).inflate(R.layout.view_item_filter_chip,null);

                    Chip chip = view.findViewById(R.id.chip);
                    chip.setText(grouping);
                    Drawable icon = UIUtils.getTintedDrawable(holder.context,R.drawable.ic_context_group_24dp, holder.context.getColor(R.color.drawer_header_background));
                    chip.setChipIcon(icon);
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if(isFilter(filter, MediaFilter.TYPE.GROUPING, grouping)) {
                                adapter.setFilter(null);
                            }else {
                                adapter.setFilter(new MediaFilter(MediaFilter.TYPE.GROUPING, StringUtils.trimToEmpty(grouping)));
                            }
                            adapter.filterItems();
                            toggleFilter();
                        }
                    });

                    if( isFilter(filter, MediaFilter.TYPE.GROUPING, grouping)) {
                       // chip.setSelected(true);
                        icon = UIUtils.getTintedDrawable(holder.context,getFilterIconRes(), holder.context.getColor(R.color.selected));
                        chip.setChipIcon(icon);
                        chip.setCloseIconVisible(true);
                        chip.setTextColor(holder.context.getColor(R.color.selected));
                    }

                    holder.mFilters.addView(chip);
                }
            }else {
                holder.mFilterBar.setVisibility(View.GONE);
            }
    }

    private boolean isFilter(MediaFilter filter, MediaFilter.TYPE type, String keyword) {
        if(filter == null) return false;
        return  (filter.getType() == type && filter.getKeyword().equalsIgnoreCase(StringUtils.trimToEmpty(keyword)));
    }


    static class HeaderViewHolder extends FlexibleViewHolder {
        Context context;
        TextView mTitle;
        TextView mSubtitle;
        ChipGroup mFilters;
        LinearLayout mFilterBar;

        HeaderViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter, true);//True for sticky
            context = view.getContext();
            mTitle = view.findViewById(R.id.header_title);
            mSubtitle = view.findViewById(R.id.header_subtitle);
            mFilters = view.findViewById(R.id.filter_group);
            mFilterBar = view.findViewById(R.id.header_filter_bar);
            setFullSpan(true);
        }

        @Override
        public String toString() {
            return super.toString() + " " + mTitle.getText();
        }
    }

    @Override
    public String toString() {
        return "HeaderItem[id=" + id +
                ", title=" + title + "]";
    }
}