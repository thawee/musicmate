package apincer.android.mmate.ui;

import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.provider.IconProviders;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.provider.CoverartFetcher;
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
            if(tag.equals(selectedSong)) return i;
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
        if(criteria.getType() == SearchCriteria.TYPE.MY_SONGS) {
            titles.add(Constants.TITLE_ALL_SONGS);
            titles.add(Constants.TITLE_INCOMING_SONGS);
            titles.add(Constants.TITLE_DUPLICATE);
            titles.add(Constants.TITLE_TO_ANALYST_DR);
            titles.add(Constants.TITLE_BROKEN);
            // titles.add(Constants.TITLE_NO_COVERART);
            // }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ &&
            //         Constants.AUDIO_SQ_DSD.equals(criteria.getKeyword())) {
            //    titles.add(Constants.TITLE_DSD_AUDIO);
        }else if(criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
            titles.add(Constants.QUALITY_AUDIOPHILE);
            titles.add(Constants.QUALITY_RECOMMENDED);
            titles.add(Constants.QUALITY_GOOD);
            titles.add(Constants.QUALITY_BAD);
        }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS) {
            titles.add(Constants.TITLE_HIGH_QUALITY);
            titles.add(Constants.TITLE_HIFI_LOSSLESS);
            titles.add(Constants.TITLE_HIRES);
            titles.add(Constants.AUDIO_SQ_PCM_MQA);
            titles.add(Constants.AUDIO_SQ_DSD);
        }else if(criteria.getType() == SearchCriteria.TYPE.GROUPING) {
            List<String> tabs = TagRepository.getActualGroupingList(context);
            titles.addAll(tabs);
        }else if(criteria.getType() == SearchCriteria.TYPE.GENRE) {
            List<String> tabs = TagRepository.getActualGenreList(context);
            titles.addAll(tabs);
        }else if(criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
            List<String> tabs = TagRepository.getPublisherList(context);
            titles.addAll(tabs);
        }else {
            titles.add(getHeaderTitle());
        }

        return titles;
    }

    public String getHeaderTitle() {
        if(criteria!=null) {
            if(criteria.getType() == SearchCriteria.TYPE.MY_SONGS) {
                /*if(Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword())) {
                    return Constants.TITLE_INCOMING_SONGS;
                }else if(Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())) {
                    return Constants.TITLE_DUPLICATE;
                }else if(Constants.TITLE_BROKEN.equals(criteria.getKeyword())) {
                    return Constants.TITLE_BROKEN;
                }else if(Constants.TITLE_NOT_DR.equals(criteria.getKeyword())) {
                    return Constants.TITLE_NOT_DR;
                }else {
                    return Constants.TITLE_ALL_SONGS;
                }*/
                return isEmpty(criteria.getKeyword())?Constants.TITLE_ALL_SONGS:criteria.getKeyword();
            } else if(criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
                if (Constants.QUALITY_AUDIOPHILE.equals(criteria.getKeyword())) {
                    return Constants.QUALITY_AUDIOPHILE;
                } else if (Constants.QUALITY_RECOMMENDED.equals(criteria.getKeyword())) {
                    return Constants.QUALITY_RECOMMENDED;
                } else if (!isEmpty(criteria.getKeyword())) {
                    return StringUtils.trimToEmpty(criteria.getKeyword());
                } else {
                    return Constants.QUALITY_GOOD;
                }
            }else if(criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
                if(isEmpty(criteria.getKeyword()) || Constants.UNKNOWN.equals(criteria.getKeyword())) {
                    return Constants.UNKNOWN;
                }else {
                    return criteria.getKeyword();
                }
            } else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS) {
                //  if(Constants.AUDIO_SQ_DSD.equals(criteria.getKeyword())) {
                //      return Constants.TITLE_DSD_AUDIO;
                //  }else {
                return criteria.getKeyword();
                //  }
            } else if(criteria.getType() == SearchCriteria.TYPE.PLAYLIST) {
                String keyword = criteria.getKeyword();
                if("SMART_LIST_FINFIN_SONGS".equals(keyword)) {
                    return "ฟังเพลงฟินๆ รินเบียร์เย็นๆ";
                }else if("SMART_LIST_FINFIN_EN_SONGS".equals(keyword)) {
                    return "ฟังเพลงสากลฟินๆ รินเบียร์เย็นๆ";
                }else if("SMART_LIST_FINFIN_TH_SONGS".equals(keyword)) {
                    return "ฟังเพลงไทยฟินๆ รินเบียร์เย็นๆ";
                }else if("SMART_LIST_RELAXED_TH_SONGS".equals(keyword)) {
                    return "ยานอนหลับ ฉบับไทยๆ";
                }else if("SMART_LIST_RELAXED_EN_SONGS".equals(keyword)) {
                    return "ยานอนหลับ ฉบับสากล";
                }else if("SMART_LIST_RELAXED_SONGS".equals(keyword)) {
                    return "ยานอนหลับ ฉบับรวมมิตร";
                }else if("SMART_LIST_ISAAN_SONGS".equals(keyword)) {
                    return "สะออนแฮง สำเนียงเสียงลำ";
                }else if("SMART_LIST_BAANTHUNG_SONGS".equals(keyword)) {
                    return "คิดถึง บ้านทุ่งท้องนา";
                }else if("SMART_LIST_CLASSIC_SONGS".equals(keyword)) {
                    return "คลาสสิคกล่อมโลก ฟังแล้วอารมณ์ดี";
                }
            } else {
                return criteria.getKeyword();
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
        return !(isEmpty(criteria.getFilterType()) && isEmpty(criteria.getFilterText()));
    }

    public boolean isSearchMode() {
        //return criteria.getType() == SearchCriteria.TYPE.SEARCH;
        return criteria.isSearchMode();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void removeMusicTag(int position, MusicTag song) {
        localDataSet.remove(song);
        notifyDataSetChanged();
    }

    public String getHeaderLabel() {
        if(criteria!=null) {
            if(criteria.getType() == SearchCriteria.TYPE.MY_SONGS) {
                return Constants.TITLE_LIBRARY;
                // }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ &&
                //         Constants.AUDIO_SQ_DSD.equals(criteria.getKeyword())) {
                //     return Constants.TITLE_DSD;
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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        long id;
        View rootView;
        View mCoverArtFrame;
        View mTitleLayout;
        // View mDynamicRangePanel;
        TextView mTitle;
        TextView mSubtitle;
        TextView mDurationView;
        TextView mFileSizeView;
        ImageView mCoverArtView;
        //  ImageView mFileSourceView;
        TextView mFileTypeView;
        // TextView mDynamicRange;
        Context mContext;
        ImageView mPlayerView;
        ImageView mAudioResolutionView;
        TriangleLabelView mNewLabelView;
        // TextView mTrackReplayGainView;
        ImageView mAudioQuality;

        public ViewHolder(View view) {
            super(view);
            // Define click listener for the ViewHolder's View
            rootView = view;
            this.mContext = view.getContext();
            this.mCoverArtFrame = view.findViewById(R.id.item_imageFrame);
            this.mTitleLayout = view.findViewById(R.id.item_title_layout);
            this.mTitle = view.findViewById(R.id.item_title);
            this.mSubtitle = view.findViewById(R.id.item_subtitle);
            this.mDurationView = view.findViewById(R.id.item_duration);
            //  this.mDynamicRange = view.findViewById(R.id.item_dr_icon);
            // this.mDynamicRangePanel = view.findViewById(R.id.item_dr_icon_panel);
            this.mCoverArtView = view.findViewById(R.id.item_image_coverart);
            this.mPlayerView = view.findViewById(R.id.item_player);
            this.mAudioResolutionView = view.findViewById(R.id.item_resolution_icon);
            //   this.mFileSourceView = view.findViewById(R.id.item_src_icon);
            this.mFileTypeView = view.findViewById(R.id.item_type_label);

            this.mFileSizeView = view.findViewById(R.id.item_file_size);
            this.mNewLabelView = view.findViewById(R.id.item_new_label);
            this.mAudioQuality = view.findViewById(R.id.item_audio_quality_icon);
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
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.view_list_item, viewGroup, false);

        return new ViewHolder(view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, @SuppressLint("RecyclerView") final int position) {
        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        MusicTag tag = localDataSet.get(position);
        holder.id = position;
        // When user scrolls, this line binds the correct selection status
        holder.rootView.setActivated(mTracker.isSelected((long) position));
        holder.rootView.setOnClickListener(view -> onListItemClick.onClick(holder.rootView, holder.getLayoutPosition()));
        ImageLoader imageLoader = SingletonImageLoader.get(holder.mContext);

        // Background, when bound the first time
        MusicTag listeningItem = MusixMateApp.getPlayerControl().getPlayingSong();
        boolean isListening = tag.equals(listeningItem);
        // if(MusicTagUtils.isFLACFile(tag)) {
        holder.mAudioQuality.setVisibility(View.VISIBLE);
        ImageRequest request = new ImageRequest.Builder(holder.mContext)
                .data(IconProviders.getTrackQualityIcon(holder.mContext, tag))
                // .crossfade(false)
                .target(new ImageViewTarget(holder.mAudioQuality))
                .build();
        imageLoader.enqueue(request);

        if (isListening) {
            //show music player icon
            // set italic
            holder.mPlayerView.setImageDrawable(MusixMateApp.getPlayerControl().getPlayerInfo().getPlayerIconDrawable());
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

        request = CoverartFetcher.builder(holder.mContext, tag)
                .data(tag)
                .size(240, 240)
                //.size(Size.ORIGINAL)
                //.scale(Scale.FILL) // Set the scale type
                //.crossfade(false)
                .target(new ImageViewTarget(holder.mCoverArtView))
                .error(imageRequest -> CoverartFetcher.getDefaultCover(holder.mContext))
                //.placeholder(R.drawable.progress)
                //.error(getDefaultNoCover(tag))
                //.error(R.drawable.no_cover2)
                //(new RoundedCornersTransformation(8, 8, 8, 8))
                .build();
        imageLoader.enqueue(request);

        // show enc i.e. PCM, MQA, DSD
        request = new ImageRequest.Builder(holder.mContext)
                .data(IconProviders.getResolutionIcon(holder.mContext, tag))
                // .crossfade(false)
                .target(new ImageViewTarget(holder.mAudioResolutionView))
                .build();
        imageLoader.enqueue(request);

        holder.mTitle.setText(MusicTagUtils.getFormattedTitle(holder.mContext, tag));
        holder.mSubtitle.setText(MusicTagUtils.getFormattedSubtitle(tag));

        // file encoding format
        Drawable resolutionBackground = IconProviders.getFileFormatBackground(holder.mContext, tag); //MusicTagUtils.getResolutionBackground(holder.mContext, tag);
        holder.mFileTypeView.setText(trimToEmpty(tag.getAudioEncoding()).toUpperCase(Locale.US));
        holder.mFileTypeView.setBackground(resolutionBackground);
        // duration
        holder.mDurationView.setText(StringUtils.formatDuration(tag.getAudioDuration(), false));

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

