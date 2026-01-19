package apincer.android.mmate.ui;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static apincer.music.core.utils.StringUtils.formatSongSize;
import static apincer.music.core.utils.StringUtils.isEmpty;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.R;
import apincer.android.mmate.coil3.CoverartFetcher;
import apincer.music.core.Constants;
import apincer.music.core.database.MusicTag;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.model.MusicFolder;
import apincer.music.core.model.SearchCriteria;
import apincer.android.mmate.ui.view.DurationView;
import apincer.android.mmate.ui.view.DynamicRangeView;
import apincer.android.mmate.ui.view.QualityIndicatorView;
import apincer.android.mmate.ui.view.TriangleLabelView;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.music.core.utils.StringUtils;
import apincer.music.core.utils.TagUtils;
import coil3.ImageLoader;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;
import coil3.target.ImageViewTarget;

public class MusicTagAdapter extends RecyclerView.Adapter<MusicTagAdapter.ViewHolder> {
    private final SearchCriteria criteria;
    private final List<MusicTag> localDataSet;
    private SelectionTracker<Long> mTracker;
    private OnListItemClick onListItemClick;
    //private long totalSize;
    private double totalDuration;
    private PlaybackService playbackService;
    private final TagRepository tagRepos;

    public void setPlaybackService(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

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
       // } else if (Constants.FILTER_TYPE_GROUPING.equals(criteria.getFilterType())) {
       //     return StringUtils.equals(tag.getGrouping(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_PATH.equals(criteria.getFilterType())) {
            return tag.getPath().startsWith(criteria.getFilterText());
        }
        return false;
    }

    public void setKeyword(String text) {
        criteria.setKeyword(text);
    }

    public void search(String text) {
        if(isEmpty(text)) {
            criteria.resetSearch();
        }else {
            criteria.searchFor(text);
        }
    }

    public void setClickListener(OnListItemClick context) {
        this.onListItemClick = context;
    }

    public int getMusicTagPosition(MediaTrack selectedSong) {
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

    public int getTotalItems() {
        return localDataSet.size();
    }

    public double getTotalDuration() {
        return totalDuration;
    }

    public void resetFilter() {
        criteria.setFilterText(null);
        criteria.setFilterType(null);
    }

    public String getHeaderTitle() {
        if(criteria!=null) {
            String keyword = trimToEmpty(criteria.getKeyword());
            if(criteria.getType() == SearchCriteria.TYPE.LIBRARY) {
                return isEmpty(keyword) ? Constants.TITLE_ALL_SONGS : keyword;
            }else if(criteria.getType() == SearchCriteria.TYPE.ARTIST) {
                return isEmpty(keyword) ? Constants.TITLE_ARTIST : keyword;
            }else if(criteria.getType() == SearchCriteria.TYPE.GENRE) {
                return isEmpty(keyword) ? Constants.TITLE_GENRE : keyword;
           // }else if(criteria.getType() == SearchCriteria.TYPE.GROUPING) {
            //    return isEmpty(keyword) ? Constants.TITLE_GROUPING : keyword;
            } else if(criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
                if (Constants.QUALITY_AUDIOPHILE.equals(keyword)) {
                    return Constants.QUALITY_AUDIOPHILE;
                } else if (Constants.QUALITY_RECOMMENDED.equals(keyword)) {
                    return Constants.QUALITY_RECOMMENDED;
                } else if (!isEmpty(keyword)) {
                    return keyword;
                } else {
                    return Constants.QUALITY_FAVORITE;
                }
            }else if(criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
                if(isEmpty(keyword) || Constants.UNKNOWN.equals(keyword)) {
                    return Constants.UNKNOWN;
                }else {
                    return keyword;
                }
            } else if(criteria.getType() == SearchCriteria.TYPE.CODEC) {
                return isEmpty(keyword) ? Constants.TITLE_CODEC : keyword;
            } else if(criteria.getType() == SearchCriteria.TYPE.PLAYLIST) {
                return isEmpty(keyword) ? Constants.TITLE_PLAYLIST : keyword;
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
           // }else if(criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
          //      return Constants.TITLE_QUALITY;
            }else if(criteria.getType() == SearchCriteria.TYPE.CODEC) {
                return Constants.TITLE_CODEC;
            //}else if(criteria.getType() == SearchCriteria.TYPE.GROUPING) {
           //     return Constants.TITLE_GROUPING;
            }else if(criteria.getType() == SearchCriteria.TYPE.GENRE) {
                return Constants.TITLE_GENRE;
            }else if(criteria.getType() == SearchCriteria.TYPE.ARTIST) {
                return Constants.TITLE_ARTIST;
          //  }else if(criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
           //     return Constants.TITLE_PUBLISHER;
            }else if(criteria.getType() == SearchCriteria.TYPE.PLAYLIST) {
                return Constants.TITLE_PLAYLIST;
            }else {
                return Constants.TITLE_LIBRARY;
            }
        }
        return Constants.TITLE_LIBRARY;
    }

    // New method to set music tags from ViewModel
    @SuppressLint("NotifyDataSetChanged")
    public void setMusicTags(List<MusicTag> tags) {
        localDataSet.clear();
        //totalSize = 0;
        totalDuration = 0;

        if(tags == null) return;

        boolean noFilters = !hasFilter();
        if(noFilters) {
            for (MusicTag tag : tags) {
                localDataSet.add(tag);
                //totalSize += tag.getFileSize();
                totalDuration += tag.getAudioDuration();
            }
        } else {
            for (MusicTag tag : tags) {
                if(!isMatchFilter(tag)) {
                    continue;
                }
                localDataSet.add(tag);
                //totalSize += tag.getFileSize();
                totalDuration += tag.getAudioDuration();
            }
        }
        notifyDataSetChanged();
    }

    public void notifyItemChanged(MediaTrack currentlyPlaying) {
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
        TextView mTitle;
        TextView mSubtitle;
        TextView mAlbum;

        TextView mFileType;

        TextView mSampleRate;
       ImageView mCoverArtView;
        Context mContext;
        ImageView mPlayerView;
        TriangleLabelView mNewLabelView;
       // ResolutionView resolutionView;
        DynamicRangeView drDbView;
        QualityIndicatorView qualityIndicatorView;
       // RatingIndicatorView ratingIndicatorView;
        DurationView mDurationView;
       // View moreActions;

        public ViewHolder(View view) {
            super(view);
            // Define click listener for the ViewHolder's View
            rootView = view;
            this.mContext = view.getContext();

            this.mCoverArtFrame = view.findViewById(R.id.item_imageFrame);
            this.mTitleLayout = view.findViewById(R.id.item_title_layout);
            this.mTitle = view.findViewById(R.id.item_title);
            this.mSubtitle = view.findViewById(R.id.item_subtitle);
            this.mAlbum = view.findViewById(R.id.item_album);
            this.mDurationView = view.findViewById(R.id.view_duration);
            this.mCoverArtView = view.findViewById(R.id.item_image_coverart);
            this.mPlayerView = view.findViewById(R.id.item_player);

           // this.mFileSizeView = view.findViewById(R.id.item_file_size);
            this.mNewLabelView = view.findViewById(R.id.item_new_label);

            this.drDbView = view.findViewById(R.id.dynamic_range_db_view);
            this.qualityIndicatorView = view.findViewById(R.id.icon_quality_indicator);
           // this.ratingIndicatorView = view.findViewById(R.id.rating_view);

           // this.moreActions = view.findViewById(R.id.item_more_actions);
            this.mFileType = view.findViewById(R.id.item_filetype);
            this.mSampleRate = view.findViewById(R.id.item_sample_rate);
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
    public MusicTagAdapter(TagRepository tagRepos, SearchCriteria criteria) {
        this.criteria = criteria;
        this.tagRepos = tagRepos;
        this.localDataSet = new ArrayList<>();
        setHasStableIds(true);
    }

    @Override
    public int getItemViewType(int position) {
        MusicTag tag = localDataSet.get(position);
        if(tag instanceof MusicFolder) {
            return 2;
        }else if (isComparing(tag)) {
            return 1;
        }else {
            return 0;
        }
    }

    // Create new views (invoked by the layout manager)
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if(viewType == 0) {
            // Create a new view, which defines the UI of the list item
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.view_list_item, parent, false);

            return new ViewHolder(view);
        }else if(viewType == 1) {
            // Create a new view, which defines the UI of the list item
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.view_list_item_compared, parent, false);

            return new ViewHolder(view);
        }else {
            // Create a new view, which defines the UI of the list item
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.view_list_folder, parent, false);

            return new ViewHolder(view);
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, @SuppressLint("RecyclerView") final int position) {
        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        MusicTag item = localDataSet.get(position);
        holder.id = position;
        /*int itemViewType = getItemViewType(position);
        if(itemViewType == 0 || itemViewType == 1) {
            onBindViewMusicTag(holder, position, item); // Pass item, not position
        } else {
            onBindViewMusicFolder(holder, (MusicFolder) item); // Pass item, not position
        }*/
        if(item instanceof MusicFolder folder) {
            onBindViewMusicFolder(holder, folder); // Pass item, not position
        }else {
            onBindViewMusicTag(holder, position, item); // Pass item, not position
        }
    }

    private void onBindViewMusicFolder(ViewHolder holder, MusicFolder item) {
        // When user scrolls, this line binds the correct selection status
       // holder.rootView.setActivated(mTracker.isSelected((long) position));
        holder.rootView.setEnabled(true);
        holder.rootView.setOnClickListener(view -> onListItemClick.onClick(holder.rootView, holder.getLayoutPosition()));
        ImageLoader imageLoader = SingletonImageLoader.get(holder.mContext);

        ImageRequest request = CoverartFetcher.builder(holder.mContext, item)
                .data(item)
                .size(240, 240)
                .target(new ImageViewTarget(holder.mCoverArtView))
               // .error(imageRequest -> CoverartFetcher.getDefaultCover(holder.mContext))
                .build();
        imageLoader.enqueue(request);

        holder.mTitle.setText(MusicTagUtils.getFormattedTitle(holder.mContext, item));
       // String subTitle = "🎵 "+formatSongSize(item.getChildCount());
        String subTitle = formatSongSize(item.getChildCount());
        subTitle = subTitle+" Songs | " + StringUtils.formatDuration(item.getAudioDuration(), true);

        //if(SearchCriteria.TYPE.ARTIST.name().equals(item.getFileType())) {
            holder.mSubtitle.setText(subTitle);
       // }
    }

    public void onBindViewMusicTag(ViewHolder holder, @SuppressLint("RecyclerView") final int position, @NotNull MusicTag tag) {
        // When user scrolls, this line binds the correct selection status
        holder.rootView.setSelected(mTracker.isSelected((long) position));
        if(tag.getAudioDuration() == 0) {
           // holder.moreActions.setEnabled(false);
            holder.rootView.setEnabled(false);
        }else {
           // holder.moreActions.setEnabled(true);
            holder.rootView.setEnabled(true);
            holder.rootView.setOnClickListener(view -> onListItemClick.onClick(holder.rootView, holder.getLayoutPosition()));
           // holder.moreActions.setOnClickListener(view -> showMoreActions(view, tag));
        }

        ImageLoader imageLoader = SingletonImageLoader.get(holder.mContext);

        // reset player icon
        holder.mPlayerView.setVisibility(GONE);
        if (holder.mTitleLayout != null) {
            holder.mTitleLayout.setBackgroundResource(R.drawable.shape_item_background); // Create this drawable
        }
        boolean isPlaying = false;
        if(playbackService != null) {
            if (tag.equals(playbackService.getNowPlayingSong())) {
                isPlaying = true;
                Drawable bg = AppCompatResources.getDrawable(holder.mContext, R.drawable.shape_round_background_playing);
               // Drawable drawable = AppCompatResources.getDrawable(holder.mContext, R.drawable.ic_equalizer_active);
                Drawable drawable = AppCompatResources.getDrawable(holder.mContext, R.drawable.ic_notification_default);
                holder.mPlayerView.setImageDrawable(drawable);
                holder.mPlayerView.setBackground(bg);
                holder.mPlayerView.setVisibility(VISIBLE);
                /*if (drawable instanceof AnimatedImageDrawable animatedImageDrawable) {
                    animatedImageDrawable.start();
                } */
            }
        }
        if(!isPlaying && MusicTagUtils.getRating(tag)>= 3) {
            holder.mPlayerView.setVisibility(VISIBLE);
            holder.mPlayerView.setImageDrawable(AppCompatResources.getDrawable(holder.mContext, R.drawable.round_favorite_border_24));
            holder.mPlayerView.setBackground(AppCompatResources.getDrawable(holder.mContext, R.drawable.bg_transparent));
            //  builder.addText("🤍");
        }

        // download label
        if (tag.isMusicManaged()) {
            holder.mNewLabelView.setVisibility(GONE);
        } else if (MusicTagUtils.isOnDownloadDir(tag)) {
            holder.mNewLabelView.setTriangleBackgroundColorResource(R.color.new_indicator_background);
            holder.mNewLabelView.setPrimaryTextColorResource(R.color.new_download_indicator_text);
            holder.mNewLabelView.setSecondaryTextColorResource(R.color.material_color_yellow_100);
            holder.mNewLabelView.setVisibility(VISIBLE);
        } else {
            holder.mNewLabelView.setTriangleBackgroundColorResource(R.color.new_indicator_background);
            holder.mNewLabelView.setPrimaryTextColorResource(R.color.new_indicator_text);
            holder.mNewLabelView.setSecondaryTextColorResource(R.color.material_color_yellow_100);
            holder.mNewLabelView.setVisibility(VISIBLE);
        }

        ImageRequest request = CoverartFetcher.builder(holder.mContext, tag)
                .data(tag)
                .size(240, 240)
                .target(new ImageViewTarget(holder.mCoverArtView))
               // .error(imageRequest -> CoverartFetcher.getDefaultCover(holder.mContext))
                .build();
        imageLoader.enqueue(request);

        holder.mTitle.setText(MusicTagUtils.getFormattedTitle(holder.mContext, tag));
        //holder.mSubtitle.setText(MusicTagUtils.getFormattedSubtitle(tag));
        holder.mSubtitle.setText(tag.getArtist());
        holder.mAlbum.setText(StringUtils.getSimplifiedAlbum(tag));

      //  holder.resolutionView.setMusicItem(tag);

        if(isComparing(tag)) {
            holder.drDbView.setVisibility(VISIBLE);
            holder.drDbView.setMusicItem(tag);
            if(holder.mFileType != null) {
                holder.mFileType.setText(tag.getFileType());
            }
            if(holder.mSampleRate != null) {
                holder.mSampleRate.setText(TagUtils.getSampleRate(tag));
            }
        }else if(tag.getDynamicRangeScore()>0){
            holder.drDbView.setVisibility(VISIBLE);
            holder.drDbView.setMusicItem(tag);
        }else{
             holder.drDbView.setVisibility(GONE);
        }
        /*
        if(TagUtils.isLossy(tag)) {
          //  holder.qualityIndicatorView.setVisibility(GONE);
            holder.drDbView.setVisibility(GONE);
        } else {
            holder.drDbView.setVisibility(VISIBLE);
            holder.drDbView.setMusicItem(tag);

          //  holder.qualityIndicatorView.setVisibility(VISIBLE);
           // holder.qualityIndicatorView.setMusicItem(tag);
        } */

        holder.qualityIndicatorView.setVisibility(VISIBLE);
        holder.qualityIndicatorView.setMusicItem(tag);

       // holder.ratingIndicatorView.setMusicItem(tag);
        holder.mDurationView.setMusicItem(tag);
    }

    private boolean isComparing(MusicTag tag) {
        if(criteria == null || tag == null) return false;

        if(criteria.getType().equals(SearchCriteria.TYPE.LIBRARY)) {
            return (!tag.isMusicManaged()) || Constants.TITLE_DUPLICATE.equals(criteria.getKeyword()) || Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword());
        }
        return !tag.isMusicManaged();
      // return criteria.getType().equals(SearchCriteria.TYPE.LIBRARY) && Constants.TITLE_DUPLICATE.equals(criteria.getKeyword());
    }

    private void showMoreActions(View anchorView, MusicTag song) {
        // 1. Create a PopupMenu
        PopupMenu popup = new PopupMenu(anchorView.getContext(), anchorView); // 'this' is the Context

        // 2. Inflate your menu resource
        popup.getMenuInflater().inflate(R.menu.item_more_actions_menu, popup.getMenu());
        // Or, if you don't want to use an XML menu, you can add items programmatically:
        // popup.getMenu().add(Menu.NONE, R.id.my_action_id, Menu.NONE, "My Action Title");

        // 3. Set an OnMenuItemClickListener to handle menu item clicks
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.play_button) {
                if(song != null && playbackService != null) {
                    playbackService.play(song);
                }
                return true;
            } else if (itemId == R.id.add_to_queue_button) {
                if(song != null) {
                    tagRepos.addToPlayingQueue(song);
                }
                return true;
            }
            return false; // Return false if the item click is not handled
        });

        // Optional: Set a dismiss listener
        popup.setOnDismissListener(menu -> {
            // Actions to perform when the popup is dismissed (optional)
        });

        // 4. Show the PopupMenu
        popup.show();
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

