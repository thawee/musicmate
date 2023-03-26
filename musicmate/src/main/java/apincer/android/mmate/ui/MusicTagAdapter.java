package apincer.android.mmate.ui;

import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.content.Context;
import android.graphics.Bitmap;
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
import apincer.android.mmate.fs.MusicCoverArtProvider;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.ui.view.TriangleLabelView;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.transform.RoundedCornersTransformation;

public class MusicTagAdapter extends RecyclerView.Adapter<MusicTagAdapter.ViewHolder> {
    private SearchCriteria criteria;
    private List<MusicTag> localDataSet;
    private SelectionTracker<Long> mTracker;
    private OnListItemClick onListItemClick;
    private long totalSize;
    private double totalDuration;

    public void loadDataSets() {
        localDataSet.clear();
        List<MusicTag> list = MusicTagRepository.findMediaTag(criteria);
        for(MusicTag tag: list) {
            localDataSet.add(tag);
            totalSize += tag.getFileSize();
            totalDuration += tag.getAudioDuration();
        }
        notifyDataSetChanged();
    }

    public void loadDataSets(SearchCriteria finalCriteria) {
        this.criteria = finalCriteria;
        loadDataSets();
    }

    public void setKeyword(String text) {
        criteria.setKeyword(text);
    }

    public void setSearchString(String text) {
        criteria.searchFor(text);
    }

    public void resetSearchString() {
        criteria.resetSearch();
    }

    public void setClickListener(OnListItemClick context) {
        this.onListItemClick = context;
    }

    public int getMusicTagPosition(MusicTag nowPlaying) {
        int i =0;
        for(MusicTag tag : localDataSet) {
            if(tag.equals(nowPlaying)) return i;
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
    }

    public List<String> getHeaderTitles(Context context) {
        List<String> titles = new ArrayList<>();
        if(criteria.getType() == SearchCriteria.TYPE.MY_SONGS) {
            titles.add(Constants.TITLE_ALL_SONGS);
            titles.add(Constants.TITLE_INCOMING_SONGS);
            titles.add(Constants.TITLE_DUPLICATE);
            titles.add(Constants.TITLE_BROKEN);
        }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ &&
                Constants.AUDIO_SQ_DSD.equals(criteria.getKeyword())) {
            titles.add(Constants.TITLE_DSD_AUDIO);
        }else if(criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
            titles.add(Constants.QUALITY_AUDIOPHILE);
            titles.add(Constants.QUALITY_RECOMMENDED);
            titles.add(Constants.QUALITY_NORMAL);
            titles.add(Constants.QUALITY_POOR);
        }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ) {
            titles.add(Constants.TITLE_HI_QUALITY);
            titles.add(Constants.TITLE_HIFI_LOSSLESS);
            titles.add(Constants.TITLE_HIRES);
            titles.add(Constants.AUDIO_SQ_PCM_MQA);
        }else if(criteria.getType() == SearchCriteria.TYPE.GROUPING) {
            List<String> tabs = MusicTagRepository.getGroupingList(context);
            titles.addAll(tabs);
        }else if(criteria.getType() == SearchCriteria.TYPE.GENRE) {
            List<String> tabs = MusicTagRepository.getGenreList(context);
            titles.addAll(tabs);
        }else if(criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
            List<String> tabs = MusicTagRepository.getPublisherList(context);
            titles.addAll(tabs);
        }else {
            titles.add(getHeaderTitle());
        }

        return titles;
    }

    public String getHeaderTitle() {
        if(criteria!=null) {
            if(criteria.getType() == SearchCriteria.TYPE.MY_SONGS) {
                if(Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword())) {
                    return Constants.TITLE_INCOMING_SONGS;
                }else if(Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())) {
                    return Constants.TITLE_DUPLICATE;
                }else if(Constants.TITLE_BROKEN.equals(criteria.getKeyword())) {
                    return Constants.TITLE_BROKEN;
                }else {
                    return Constants.TITLE_ALL_SONGS;
                }
            } else if(criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
                if (Constants.QUALITY_AUDIOPHILE.equals(criteria.getKeyword())) {
                    return Constants.QUALITY_AUDIOPHILE;
                } else if (Constants.QUALITY_RECOMMENDED.equals(criteria.getKeyword())) {
                    return Constants.QUALITY_RECOMMENDED;
                } else if (!isEmpty(criteria.getKeyword())) {
                    return StringUtils.trimToEmpty(criteria.getKeyword());
                } else {
                    return Constants.QUALITY_NORMAL;
                }
            }else if(criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
                if(isEmpty(criteria.getKeyword()) || Constants.UNKNOWN_PUBLISHER.equals(criteria.getKeyword())) {
                    return Constants.UNKNOWN_PUBLISHER;
                }else {
                    return criteria.getKeyword();
                }
            } else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ) {
                if(Constants.AUDIO_SQ_DSD.equals(criteria.getKeyword())) {
                    return Constants.TITLE_DSD_AUDIO;
                }else {
                    return criteria.getKeyword();
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

    public MusicTag getContent(int position) {
       // for(MusicTag tag: localDataSet) {
       //     if(tag.getId() == position) return tag;
       // }
        if(position == RecyclerView.NO_POSITION) return null;
        return localDataSet.get(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        long id;
        View rootView;
        View mCoverArtFrame;
        View mTitleLayout;
        TextView mTitle;
        TextView mSubtitle;
        TextView mDurationView;
        TextView mFileSizeView;
        ImageView mCoverArtView;
        ImageView mFileSourceView;
        ImageView mFileBrokenView;
        TextView mFileTypeView;
        TextView mDynamicRange;
        Context mContext;
        ImageView mPlayerView;
        TextView mMediaQualityView;
        ImageView mAudioHiResView;
        //ImageView mAudioLoudnessView;
        TriangleLabelView mNewLabelView;
        // TextView mTrackReplayGainView;

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
            this.mDynamicRange = view.findViewById(R.id.item_dr_icon);
            this.mMediaQualityView = view.findViewById(R.id.item_media_quality_label);
            this.mCoverArtView = view.findViewById(R.id.item_image_coverart);
            this.mPlayerView = view.findViewById(R.id.item_player);
            this.mAudioHiResView = view.findViewById(R.id.item_hires_icon);
            this.mFileSourceView = view.findViewById(R.id.item_src_icon);
            this.mFileBrokenView = view.findViewById(R.id.item_broken_file_icon);
            this.mFileTypeView = view.findViewById(R.id.item_type_label);

            this.mFileSizeView = view.findViewById(R.id.item_file_size);
            this.mNewLabelView = view.findViewById(R.id.item_new_label);
        }

        public ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
            return new ItemDetailsLookup.ItemDetails<Long>() {
                @Override
                public int getPosition() {
                    return getAbsoluteAdapterPosition();
                }

                @Nullable
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

        KeyProvider(MusicTagAdapter adapter) {
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
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.view_list_item, viewGroup, false);

        return new ViewHolder(view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        MusicTag tag = localDataSet.get(position);
        holder.id = (long)position; //tag.getId();
        // When user scrolls, this line binds the correct selection status
        holder.rootView.setActivated(mTracker.isSelected((long) position));
        holder.rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onListItemClick.onClick(holder.rootView, holder.getLayoutPosition());
            }
        });
        ImageLoader imageLoader = Coil.imageLoader(holder.mContext);

        // Background, when bound the first time
        MusicTag listeningItem = MusixMateApp.getPlayingSong();
        boolean isListening = tag.equals(listeningItem);

      //  holder.rootView.setOnClickListener(clickListener);
      //  holder.rootView.setOnLongClickListener(longClickListener);

        if (!StringUtils.isEmpty(tag.getMediaQuality())) {
            if(Constants.QUALITY_AUDIOPHILE.equals(tag.getMediaQuality())) {
                holder.mMediaQualityView.setTextColor(holder.mContext.getColor(R.color.audiophile_label1));
            }else {
                holder.mMediaQualityView.setTextColor(holder.mContext.getColor(R.color.audiophile_label2));
            }
            holder.mMediaQualityView.setText(StringUtils.getAbvByUpperCase(tag.getMediaQuality()));
            holder.mMediaQualityView.setVisibility(View.VISIBLE);
        } else {
            holder.mMediaQualityView.setVisibility(View.GONE);
        }

        if (isListening) {
            //show music player icon
            // set italic
            holder.mPlayerView.setImageDrawable(MusixMateApp.getPlayerInfo().getPlayerIconDrawable());
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

        ImageRequest request;
        // Show AlbumArt
       request = new ImageRequest.Builder(holder.mContext)
               // .data(MusicTagUtils.getCoverArt(holder.mContext, tag))
               .data(MusicCoverArtProvider.getUriForMusicTag(tag))
                // .size(800, 800)
                .crossfade(false)
                .target(holder.mCoverArtView)
                .transformations(new RoundedCornersTransformation(8, 8, 8, 8))
                .build();
        imageLoader.enqueue(request);

        // show enc i.e. PCM, MQA, DSD
        request = new ImageRequest.Builder(holder.mContext)
                .data(MusicTagUtils.getEncResolutionIcon(holder.mContext, tag))
                .crossfade(false)
                .target(holder.mAudioHiResView)
                .build();
        imageLoader.enqueue(request);

        holder.mTitle.setText(MusicTagUtils.getFormattedTitle(holder.mContext, tag));
        holder.mSubtitle.setText(MusicTagUtils.getFormattedSubtitle(tag));

        // file format
        Drawable resolutionBackground = MusicTagUtils.getResolutionBackground(holder.mContext, tag);
        holder.mFileTypeView.setText(trimToEmpty(tag.getFileFormat()).toUpperCase(Locale.US));
        holder.mFileTypeView.setBackground(resolutionBackground);

        holder.mFileBrokenView.setVisibility(MusicTagUtils.isFileCouldBroken(tag)?View.VISIBLE:View.GONE);

        // Dynamic Range
        resolutionBackground = MusicTagUtils.getResolutionBackground(holder.mContext, tag);
        holder.mDynamicRange.setBackground(resolutionBackground);
        holder.mDynamicRange.setText(MusicTagUtils.getTrackDRandGainString(tag));
        // duration
        holder.mDurationView.setText(tag.getAudioDurationAsString());

        // file size
        holder.mFileSizeView.setText(StringUtils.formatStorageSize(tag.getFileSize()));
        Bitmap srcBmp = MusicTagUtils.getSourceIcon(holder.mContext, tag);
        if(srcBmp!=null) {
            holder.mFileSourceView.setImageBitmap(srcBmp);
            holder.mFileSourceView.setVisibility(View.VISIBLE);
        }else {
            holder.mFileSourceView.setVisibility(View.GONE);
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return localDataSet.size();
    }
}

