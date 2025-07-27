package apincer.android.mmate.ui;

import static apincer.android.mmate.utils.MusicTagUtils.getRating;
import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;

import com.vanniktech.textbuilder.TextBuilder;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.PlaylistRepository;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.repository.model.SearchCriteria;
import apincer.android.mmate.provider.CoverartFetcher;
import apincer.android.mmate.ui.view.DynamicRangeDbView;
import apincer.android.mmate.ui.view.ResolutionView;
import apincer.android.mmate.ui.view.TriangleLabelView;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import coil3.ImageLoader;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;
import coil3.target.ImageViewTarget;

public class MusicTagAdapter extends RecyclerView.Adapter<MusicTagAdapter.ViewHolder> {
    private final SearchCriteria criteria;
    private final List<MusicTag> localDataSet;
    private SelectionTracker<Long> mTracker;
    private OnListItemClick onListItemClick;
    private long totalSize;
    private double totalDuration;

    public boolean isMatchFilter(MusicTag tag) {
        if(criteria==null || isEmpty(criteria.getFilterType())) {
            return true;
        } else if (Constants.FILTER_TYPE_ALBUM.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getAlbum(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_ARTIST.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getArtist(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_ALBUM_ARTIST.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getAlbumArtist(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_GENRE.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getGenre(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_PUBLISHER.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getPublisher(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_GROUPING.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getGrouping(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_PATH.equals(criteria.getFilterType())) {
            return tag.getPath().startsWith(criteria.getFilterText());
        }
        return false;
    }

    public void setKeyword(String text) {
        criteria.setKeyword(text);
    }

    public void setSearchString(String text) {
        if(isEmpty(text)) {
            criteria.resetSearch();
        }else {
            criteria.searchFor(text);
        }
    }

    public void setClickListener(OnListItemClick context) {
        this.onListItemClick = context;
    }

    public int getMusicTagPosition(MusicTag selectedSong) {
        int i =0;
        for(MusicTag tag : localDataSet) {
            if(tag != null && tag.equals(selectedSong)) return i;
            i++;
        }
        return RecyclerView.NO_POSITION;
    }

    public SearchCriteria getCriteria() {
        return criteria;
    }

    public int getTotalSongs() {
        return localDataSet.size();
    }

    public long getTotalSize() {
        return  totalSize;
    }

    public double getTotalDuration() {
        return totalDuration;
    }

    public void resetFilter() {
        criteria.setFilterText(null);
        criteria.setFilterType(null);
    }

    public List<String> getHeaderTitles(Context context) {
        List<String> titles = new ArrayList<>();
        if(criteria.getType() == SearchCriteria.TYPE.LIBRARY) {
            titles.add(Constants.TITLE_ALL_SONGS);
            titles.add(Constants.TITLE_INCOMING_SONGS);
            titles.add(Constants.TITLE_DUPLICATE);
            titles.add(Constants.TITLE_TO_ANALYST_DR);
            titles.add(Constants.TITLE_BROKEN);
        }else if(criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
            titles.add(Constants.QUALITY_AUDIOPHILE);
            titles.add(Constants.QUALITY_RECOMMENDED);
            titles.add(Constants.QUALITY_GOOD);
            titles.add(Constants.QUALITY_BAD);
        }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS) {
            titles.add(Constants.TITLE_HIGH_QUALITY);
            titles.add(Constants.TITLE_HIFI_LOSSLESS);
            titles.add(Constants.TITLE_HIRES);
            titles.add(Constants.TITLE_MASTER_AUDIO);
            titles.add(Constants.TITLE_DSD);
        }else if(criteria.getType() == SearchCriteria.TYPE.GROUPING) {
            List<String> tabs = TagRepository.getActualGroupingList(context);
            titles.addAll(tabs);
        }else if(criteria.getType() == SearchCriteria.TYPE.GENRE) {
            List<String> tabs = TagRepository.getActualGenreList(context);
            titles.addAll(tabs);
        }else if(criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
            List<String> tabs = TagRepository.getPublisherList(context);
            titles.addAll(tabs);
        }else if(criteria.getType() == SearchCriteria.TYPE.PLAYLIST) {
            PlaylistRepository.initPlaylist(context);
            titles.addAll(PlaylistRepository.getPlaylistNames());
        }else {
            titles.add(getHeaderTitle());
        }

        return titles;
    }

    public String getHeaderTitle() {
        if(criteria!=null) {
            String keyword = trimToEmpty(criteria.getKeyword());
            if(criteria.getType() == SearchCriteria.TYPE.LIBRARY) {
                return isEmpty(criteria.getKeyword())?Constants.TITLE_ALL_SONGS:keyword;
            } else if(criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
                if (Constants.QUALITY_AUDIOPHILE.equals(keyword)) {
                    return Constants.QUALITY_AUDIOPHILE;
                } else if (Constants.QUALITY_RECOMMENDED.equals(keyword)) {
                    return Constants.QUALITY_RECOMMENDED;
                } else if (!isEmpty(keyword)) {
                    return keyword;
                } else {
                    return Constants.QUALITY_GOOD;
                }
            }else if(criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
                if(isEmpty(keyword) || Constants.UNKNOWN.equals(keyword)) {
                    return Constants.UNKNOWN;
                }else {
                    return keyword;
                }
            } else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS) {
                return keyword;
            } else if(criteria.getType() == SearchCriteria.TYPE.PLAYLIST) {
               if(isEmpty(keyword)) {
                   return Constants.TITLE_PLAYLIST;
               }
               return keyword;
            } else {
                return keyword;
            }
        }
        return Constants.TITLE_ALL_SONGS;
    }

    public void injectTracker(SelectionTracker<Long> mTracker) {
        this.mTracker = mTracker;
    }

    public MusicTag getMusicTag(int position) {
        if(position == RecyclerView.NO_POSITION) return null;
        if(position >=localDataSet.size()) return null;
        return localDataSet.get(position);
    }

    public void setType(SearchCriteria.TYPE type) {
        criteria.setType(type);
    }

    public boolean hasFilter() {
        return !(isEmpty(criteria.getFilterType()));
    }

    public boolean isSearchMode() {
        return criteria.isSearchMode();
    }

    public String getHeaderLabel() {
        if(criteria!=null) {
            if(criteria.getType() == SearchCriteria.TYPE.LIBRARY) {
                return Constants.TITLE_LIBRARY;
            }else if(criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
                return Constants.TITLE_QUALITY;
            }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS) {
                return Constants.TITLE_RESOLUTION;
            }else if(criteria.getType() == SearchCriteria.TYPE.GROUPING) {
                return Constants.TITLE_GROUPING;
            }else if(criteria.getType() == SearchCriteria.TYPE.GENRE) {
                return Constants.TITLE_GENRE;
            }else if(criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
                return Constants.TITLE_PUBLISHER;
            }else if(criteria.getType() == SearchCriteria.TYPE.PLAYLIST) {
                return Constants.TITLE_PLAYLIST;
            }else {
                return Constants.TITLE_LIBRARY;
            }
        }
        return Constants.TITLE_LIBRARY;
    }

    public boolean isFirstItem(Context context) {
        return getHeaderTitle().equals(getHeaderTitles(context).get(0));
    }

    public void resetSelectedItem() {
        criteria.setKeyword("");
    }

    // New method to set music tags from ViewModel
    @SuppressLint("NotifyDataSetChanged")
    public void setMusicTags(List<MusicTag> tags) {
        localDataSet.clear();
        totalSize = 0;
        totalDuration = 0;

        if(tags == null) return;

        boolean noFilters = !hasFilter();
        if(noFilters) {
            for (MusicTag tag : tags) {
                localDataSet.add(tag);
                totalSize += tag.getFileSize();
                totalDuration += tag.getAudioDuration();
            }
        } else {
            for (MusicTag tag : tags) {
                if(!isMatchFilter(tag)) {
                    continue;
                }
                localDataSet.add(tag);
                totalSize += tag.getFileSize();
                totalDuration += tag.getAudioDuration();
            }
        }
        notifyDataSetChanged();
    }

    public void notifyItemChanged(MusicTag currentlyPlaying) {
        if(currentlyPlaying == null) return;

        int previousPosition = getMusicTagPosition(currentlyPlaying);
        if (previousPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousPosition);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        long id;
        View rootView;
        View mCoverArtFrame;
        View mTitleLayout;
        // View mDynamicRangePanel;
        TextView mTitle;
        TextView mSubtitle;
       // TextView mDurationView;
        TextView mFileSizeView;
        ImageView mCoverArtView;
        Context mContext;
        ImageView mPlayerView;
        //ImageView mAudioResolutionView;
        TriangleLabelView mNewLabelView;
        ResolutionView resolutionView;
        DynamicRangeDbView drDbView;
       // TextView drTextView;
        TextView ratingTextView;

        public ViewHolder(View view) {
            super(view);
            // Define click listener for the ViewHolder's View
            rootView = view;
            this.mContext = view.getContext();

            this.mCoverArtFrame = view.findViewById(R.id.item_imageFrame);
            this.mTitleLayout = view.findViewById(R.id.item_title_layout);
            this.mTitle = view.findViewById(R.id.item_title);
            this.mSubtitle = view.findViewById(R.id.item_subtitle);
           // this.mDurationView = view.findViewById(R.id.item_duration);
            this.mCoverArtView = view.findViewById(R.id.item_image_coverart);
            this.mPlayerView = view.findViewById(R.id.item_player);

            this.mFileSizeView = view.findViewById(R.id.item_file_size);
            this.mNewLabelView = view.findViewById(R.id.item_new_label);
           // this.mAudioQuality = view.findViewById(R.id.item_audio_quality_icon);

            this.resolutionView = view.findViewById(R.id.icon_resolution);
            this.drDbView = view.findViewById(R.id.dynamic_range_db_view);
           // this.drTextView = view.findViewById(R.id.dynamic_range);
            this.ratingTextView = view.findViewById(R.id.rating);
        }

        public ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
            return new ItemDetailsLookup.ItemDetails<>() {
                @Override
                public int getPosition() {
                    return getAbsoluteAdapterPosition();
                }

                @Override
                public Long getSelectionKey() {
                    return id;
                }
            };

        }
    }

    static class DetailsLookup extends ItemDetailsLookup<Long> {

        private final RecyclerView recyclerView;

        DetailsLookup(RecyclerView recyclerView) {
            this.recyclerView = recyclerView;
        }

        @Nullable
        @Override
        public ItemDetails<Long> getItemDetails(@NonNull MotionEvent e) {
            View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
            if (view != null) {
                RecyclerView.ViewHolder viewHolder = recyclerView.getChildViewHolder(view);
                if (viewHolder instanceof ViewHolder) {
                    return ((ViewHolder) viewHolder).getItemDetails();
                }
            }
            return null;
        }
    }

    static class KeyProvider extends ItemKeyProvider<Long> {

        KeyProvider( ) {
            super(ItemKeyProvider.SCOPE_MAPPED);
        }

        @Nullable
        @Override
        public Long getKey(int position) {
            return (long) position;
        }

        @Override
        public int getPosition(@NonNull Long key) {
            long value = key;
            return (int) value;
        }
    }

    public interface OnListItemClick {
        void onClick(View view, int position);
    }

    /**
     * Initialize the dataset of the Adapter
     *
     */
    public MusicTagAdapter(SearchCriteria criteria) {
        this.criteria = criteria;
        this.localDataSet = new ArrayList<>();
        setHasStableIds(true);
    }

    // Create new views (invoked by the layout manager)
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.view_list_item, parent, false);

        return new ViewHolder(view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, @SuppressLint("RecyclerView") final int position) {
        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        MusicTag item = localDataSet.get(position);
        holder.id = position;
        onBindViewHolder(holder, position, item); // Pass item, not position
    }

    public void onBindViewHolder(ViewHolder holder, @SuppressLint("RecyclerView") final int position, MusicTag tag) {
        // When user scrolls, this line binds the correct selection status
        holder.rootView.setActivated(mTracker.isSelected((long) position));
        holder.rootView.setOnClickListener(view -> onListItemClick.onClick(holder.rootView, holder.getLayoutPosition()));
        ImageLoader imageLoader = SingletonImageLoader.get(holder.mContext);

        // Background, when bound the first time
        MusicTag listeningItem = MusixMateApp.getPlayerControl().getPlayingSong();
        boolean isListening = tag.equals(listeningItem);

        if (isListening) {
            //show music player icon
            // set italic
            holder.mPlayerView.setImageResource(R.drawable.round_play_circle_outline_24);
            holder.mPlayerView.setVisibility(View.VISIBLE);
            holder.mTitle.setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC);
        } else {
            holder.mPlayerView.setVisibility(View.GONE);
            holder.mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        }

        // download label
        if (tag.isMusicManaged()) {
            holder.mNewLabelView.setVisibility(View.GONE);
        } else if (MusicTagUtils.isOnDownloadDir(tag)) {
            holder.mNewLabelView.setTriangleBackgroundColorResource(R.color.material_color_red_900);
            holder.mNewLabelView.setPrimaryTextColorResource(R.color.grey200);
            holder.mNewLabelView.setSecondaryTextColorResource(R.color.material_color_yellow_100);
            holder.mNewLabelView.setVisibility(View.VISIBLE);
        } else {
            holder.mNewLabelView.setTriangleBackgroundColorResource(R.color.material_color_yellow_200);
            holder.mNewLabelView.setPrimaryTextColorResource(R.color.grey800);
            holder.mNewLabelView.setSecondaryTextColorResource(R.color.material_color_yellow_100);
            holder.mNewLabelView.setVisibility(View.VISIBLE);
        }

        ImageRequest request = CoverartFetcher.builder(holder.mContext, tag)
                .data(tag)
                .size(240, 240)
                .target(new ImageViewTarget(holder.mCoverArtView))
                .error(imageRequest -> CoverartFetcher.getDefaultCover(holder.mContext))
                .build();
        imageLoader.enqueue(request);

        holder.mTitle.setText(MusicTagUtils.getFormattedTitle(holder.mContext, tag));
        holder.mSubtitle.setText(MusicTagUtils.getFormattedSubtitle(tag));

        holder.resolutionView.setMusicItem(tag);
        holder.drDbView.setMusicItem(tag);

       /* TextBuilder builder = new TextBuilder(holder.mContext);
       // builder.addText("DR ");
        int drsColor = getDRScoreColor(holder.mContext, (int) tag.getDynamicRangeScore());
        String drsLabel = "DR"+ StringUtils.trim(getDynamicRangeScore(tag),"--");
        builder.addColoredText(drsLabel, drsColor);
        builder.into(holder.drTextView);
        holder.drTextView.setBackground(getDRScoreBackgroundColor(holder.mContext, (int) tag.getDynamicRangeScore()));
        */

        TextBuilder builder = new TextBuilder(holder.mContext);
       // int ratingColor = holder.mContext.getColor(R.color.audiophile_background); // getDRScoreColor(holder.mContext, (int) tag.getDynamicRangeScore());
        int rating = getRating(tag);
        if(rating >= 4) {
           // builder.addColoredText("\u272D\u272D", ratingColor);
            builder.addText("\u2764");
        }else {
           // builder.addText("\u2729\u2729");
            builder.addText("\u2661");
        }
        builder.into(holder.ratingTextView);
        // file encoding format
       // Drawable resolutionBackground = IconProviders.getFileFormatBackground(holder.mContext, tag); //MusicTagUtils.getResolutionBackground(holder.mContext, tag);
       // holder.mFileTypeView.setText(trimToEmpty(tag.getAudioEncoding()).toUpperCase(Locale.US));
        //holder.mFileTypeView.setBackground(resolutionBackground);
        // duration
      //  holder.mDurationView.setText(StringUtils.formatDuration(tag.getAudioDuration(), false));

        // file size
        holder.mFileSizeView.setText(StringUtils.formatStorageSize(tag.getFileSize()));
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return localDataSet.size();
    }

    @Override
    public long getItemId(int position) {
        return getMusicTag(position).getId();
    }
}

