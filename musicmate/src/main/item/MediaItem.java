package apincer.android.mmate.item;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.Serializable;
import java.util.List;

import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;
import apincer.android.mmate.fs.EmbedCoverArtProvider;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.service.MusicListeningService;
import apincer.android.mmate.ui.view.TriangleLabelView;
import apincer.android.mmate.utils.AudioTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import coil.Coil;
import coil.request.LoadRequest;
import coil.transform.RoundedCornersTransformation;

public class MediaItem implements Serializable {

    private volatile AudioTag tag;
    private volatile AudioTag pendingTag;
    /* The header of this item */
    private HeaderItem header;
    private View mManagedSongView;

    public MediaItem(AudioTag tag) {
        this.tag = tag;
    }

    public MediaItem(AudioTag mdata, HeaderItem header) {
        this(mdata);
        this.header = header;
    }

    public AudioTag getTag() {
        return tag;
    }

    public AudioTag getPendingTag() {
        return pendingTag;
    }

    public String getPath() {
        return tag.getPath();
    }

    public String getObsoletePath() {
        return tag.getObsoletePath();
    }


    public String getTitle() {
        return StringUtils.trimToEmpty(tag.getTitle());
    }

    public String getFormatTitle(Context context) {
        String title = getTitle();
        if(Preferences.isShowTrackNumber(context)) {
            String track = StringUtils.trimToEmpty(getTag().getTrack());
            if(track.startsWith("0")) {
                track = track.substring(1,track.length());
            }
            if(!StringUtils.isEmpty(track)) {
                title = track + ". " + title;
            }
        }
        return title;
    }

    public String getSubtitle() {
        String album = StringUtils.trimTitle(tag.getAlbum());
        String artist = StringUtils.trimTitle(tag.getArtist());
        if (StringUtils.isEmpty(artist)) {
            artist = StringUtils.trimTitle(tag.getAlbumArtist());
        }
        if (StringUtils.isEmpty(album) && StringUtils.isEmpty(artist)) {
            return StringUtils.UNKNOWN_CAP + StringUtils.ARTIST_SEP + StringUtils.UNKNOWN_CAP;
        } else if (StringUtils.isEmpty(album)) {
            return artist;
        } else if (StringUtils.isEmpty(artist)) {
            return StringUtils.UNKNOWN_CAP + StringUtils.ARTIST_SEP + album;
        }
        return StringUtils.truncate(artist, 40) + StringUtils.ARTIST_SEP + album;
    }

    public String getArtist() {
        return StringUtils.trimTitle(tag.getArtist());
    }

    public String getAlbum() {
        return StringUtils.trimTitle(tag.getAlbum());
    }

    public String getDirectory() {
        String path = tag.getPath();
        File file = new File(path);
        return file.getParentFile().getAbsolutePath();
    }

    public View getManagedSongView() {
        return mManagedSongView;
    }

    /*
    @Override
    public void updateDiskCacheKey(MessageDigest messageDigest) {
        messageDigest.update(("" + getPath() + metadata.getLastModified()).getBytes());
    } */

    @Override
    public boolean equals(Object inObject) {
        if(inObject==null) return false;
        if (inObject instanceof MediaItem) {
            MediaItem inItem = (MediaItem) inObject;
            if (this.getTag().getId() == inItem.getTag().getId()) {
                return true;
            }
            return tag.getObsoletePath() != null && tag.getObsoletePath().equals(inItem.getPath());
        }
        return false;
    }

    @Override
    public int getLayoutRes() {
        if(getTag().getResultType() == SearchCriteria.RESULT_TYPE.ALBUM || getTag().getResultType() == SearchCriteria.RESULT_TYPE.ARTIST) {
            return R.layout.view_list_item_grouping;
        }else {
            return R.layout.view_list_item;
        }
    }


    @Override
    public void bindViewHolder(final FlexibleAdapter adapter, final MediaItem.MediaItemViewHolder holder, int position, List payloads) {
        Context context = holder.itemView.getContext();
        if(getTag().getResultType() == SearchCriteria.RESULT_TYPE.ARTIST || getTag().getResultType() == SearchCriteria.RESULT_TYPE.ALBUM) {
            bindMediaGroupingView(context, adapter, holder, position, payloads);
        }else {
            bindMediaItemView(context, adapter, holder, position, payloads);
        }
    }

    private void bindMediaGroupingView(Context context, FlexibleAdapter adapter, MediaItemViewHolder holder, int position, List payloads) {
        holder.mHeadTitle.setText(StringUtils.formatTitle(getTitle()));
    }

    private void bindMediaItemView(final Context context, final FlexibleAdapter adapter, final MediaItem.MediaItemViewHolder holder, int position, List payloads) {
        holder.mediaPath = getPath();
        this.mManagedSongView = holder.mNotificationView;

        // When user scrolls, this line binds the correct selection status
        holder.itemView.setActivated(adapter.isSelected(position));
        // Background, when bound the first time

        if (payloads.size() == 0) {
            Drawable drawable = DrawableUtils.getSelectableBackgroundCompat(
                    Color.WHITE, Color.parseColor("#dddddd"), //Same color of divider
                    DrawableUtils.getColorControlHighlight(context));
            holder.itemView.setBackground(drawable);
        }

        holder.bindNotification(MediaItem.this);
      //  MediaBrowserActivity.MediaItemAdapter mediaItemAdapter = ((MediaBrowserActivity.MediaItemAdapter) adapter);
      /****
        mediaItemAdapter.getGlide()
                .load(this)
                .into(holder.mCoverArtView);
       ****/
        LoadRequest request = LoadRequest.builder(context)
                .data(EmbedCoverArtProvider.getUriForMediaItem(MediaItem.this))
                .crossfade(true)
                .target(holder.mCoverArtView)
                .transformations(new RoundedCornersTransformation(8,8,8,8))
                .build();
        Coil.execute(request);
        if (adapter.isSelectAll() || adapter.isLastItemInActionMode()) {
            // Consume the Animation
            holder.setSelectionAnimation(adapter.isSelected(position));
        } else {
            // Display the current flip status
            holder.setAnimation(adapter.isSelected(position));
        }

        if (holder.mediaPath.equals(getPath())) {
            holder.mTitle.setText(getFormatTitle(holder.getContext()));
            holder.mSubtitle.setText(getSubtitle());

           // int borderColor = holder.getContext().getColor(R.color.black);
          //  int textColor = holder.getContext().getColor(R.color.black);
          //  int qualityColor = getSampleRateColor(holder.getContext());

            if((tag.isLossless() && !tag.isMQA()) || !tag.isLossless()) { // && !(metadata.isMQA() || metadata.isPCMHiRes192() || metadata.isPCMHiRes88_96() || metadata.isPCMHiRes44())) {
                holder.mBitrateView.setVisibility(View.VISIBLE);
                //holder.mBitrate.setText(StringUtils.getFormatedAudioBitRate(metadata.getAudioBitRate()));
               // holder.mBitrateView.setImageBitmap(MediaItemUtils.createBitmapFromText(holder.getContext(), 120, 32, StringUtils.getFormatedAudioBitRate(metadata.getAudioBitRate()), textColor,borderColor, qualityColor));
                holder.mBitrateView.setImageBitmap(AudioTagUtils.getBitRateIcon(holder.getContext(),this));
            }else {
                holder.mBitrateView.setVisibility(View.GONE);
            }

           // holder.mSamplingRateView.setImageBitmap(MediaItemUtils.createBitmapFromText(holder.getContext(), Constants.INFO_SAMPLE_RATE_WIDTH, 32, getMetadata().getAudioBitCountAndSampleRate(), textColor,borderColor, qualityColor));
            holder.mSamplingRateView.setImageBitmap(AudioTagUtils.getSampleRateIcon(holder.getContext(), this));
            // holder.mDurationView.setImageBitmap(MediaItemUtils.createBitmapFromText(holder.getContext(), 80, 32, getMetadata().getAudioDurationAsString(), textColor,borderColor, qualityColor));
            holder.mDurationView.setImageBitmap(AudioTagUtils.getDurationIcon(holder.getContext(), this));
            // holder.mFileSizeView.setImageBitmap(MediaItemUtils.createBitmapFromText(holder.getContext(), 100, 32, getMetadata().getMediaSize(), textColor,borderColor, qualityColor));
            holder.mFileSizeView.setImageBitmap(AudioTagUtils.getFileSizeIcon(holder.getContext(), this));

           // holder.mFileFormat.setImageBitmap(MediaItemUtils.createBitmapFromText(holder.getContext(), 60, 32, getMetadata().getAudioEncoding(), Color.WHITE ,Color.WHITE, holder.getContext().getColor(getEncodingColorId())));
            holder.mFileFormat.setImageBitmap(AudioTagUtils.getFileFormatIcon(holder.getContext(), this));

            // In case of searchText matches with Title or with a field this will be highlighted
            if (adapter.hasFilter()) {
                MediaFilter filter = (MediaFilter) adapter.getFilter(MediaFilter.class);
                if(filter!=null) {
                    holder.highlightSearch(filter.getKeyword());
                }
            }
        }
    }

    @Override
    public boolean filter(MediaFilter constraint) {
		if(constraint==null) {
			return true;
		}

        if(constraint.getType() == MediaFilter.TYPE.ARTIST) {
            return StringUtils.contains(getTag().getArtist(), constraint.getKeyword());
        }else if(constraint.getType() == MediaFilter.TYPE.ALBUM) {
            return StringUtils.compare(getTag().getAlbum(), constraint.getKeyword());
        }else if(constraint.getType() == MediaFilter.TYPE.ALBUM_ARTIST) {
            return StringUtils.compare(getTag().getAlbumArtist(), constraint.getKeyword());
        }else if(constraint.getType() == MediaFilter.TYPE.GENRE) {
            return StringUtils.compare(getTag().getGenre(), constraint.getKeyword());
        }else if(constraint.getType() == MediaFilter.TYPE.GROUPING) {
            if(SearchCriteria.DEFAULT_MUSIC_GROUPING.equalsIgnoreCase(constraint.getKeyword())) {
                return StringUtils.isEmpty(getTag().getGrouping());
            }else {
                return StringUtils.compare(getTag().getGrouping(), constraint.getKeyword());
            }
        }else if(constraint.getType() == MediaFilter.TYPE.PATH) {
            return StringUtils.startsWith(constraint.getKeyword(), getTag().getMediaPath());
        }

        return (StringUtils.contains(getTag().getTitle(), constraint.getKeyword())
                || StringUtils.contains(getTag().getArtist(), constraint.getKeyword())
                || StringUtils.contains(getTag().getAlbum(), constraint.getKeyword())
                || StringUtils.contains(getTag().getAlbumArtist(), constraint.getKeyword())
                || StringUtils.contains(getTag().getGenre(), constraint.getKeyword())
                || StringUtils.contains(tag.getMediaPath(), constraint.getKeyword()));

		/*
		if(StringUtils.startsWith(constraint, "artist:")) {
			constraint = constraint.substring("artist:".length());
			return StringUtils.contains(getMetadata().getArtist(), constraint);
        }else if(StringUtils.startsWith(constraint, "album:")) {
            constraint = constraint.substring("album:".length());
            return StringUtils.compare(getMetadata().getAlbum(), constraint);
        }else if(StringUtils.startsWith(constraint, "albumartist:")) {
			constraint = constraint.substring("albumartist:".length());
			return StringUtils.compare(getMetadata().getAlbumArtist(), constraint);
		}else if(StringUtils.startsWith(constraint, "genre:")) {
            constraint = constraint.substring("genre:".length());
            return StringUtils.compare(getMetadata().getGenre(), constraint);
        }else if(StringUtils.startsWith(constraint, "grouping:")) {
            constraint = constraint.substring("grouping:".length());
            if(SearchCriteria.DEFAULT_MUSIC_GROUPING.equalsIgnoreCase(constraint)) {
                return StringUtils.isEmpty(getMetadata().getGrouping());
            }else {
                return StringUtils.compare(getMetadata().getGrouping(), constraint);
            }
        }else if(StringUtils.startsWith(constraint, "directory:")) {
			constraint = constraint.substring("directory:".length());
			return StringUtils.startsWith(constraint, getMetadata().getMediaPath());
		}else if(StringUtils.startsWith(constraint, "similartitle:")) {
            constraint = constraint.substring("similartitle:".length());
            return (StringUtils.similarity(constraint, getMetadata().getTitle()) > Constants.MIN_TITLE_ONLY);
        }
		
        return (StringUtils.contains(getMetadata().getTitle(), constraint)
               || StringUtils.contains(getMetadata().getArtist(), constraint)
               || StringUtils.contains(getMetadata().getAlbum(), constraint)
                || StringUtils.contains(getMetadata().getAlbumArtist(), constraint)
                || StringUtils.contains(getMetadata().getGenre(), constraint)
               || StringUtils.contains(metadata.getMediaPath(), constraint));

		 */
    }

    @Override
    public String toString() {
        return getTitle();
    }

    public AudioTag getOrCreatePendingTag() {
        if(pendingTag ==null) {
            pendingTag = tag.clone();
        }
        return pendingTag;
    }

    public void resetPendingTag(boolean updateMetadata) {
        if(updateMetadata) {
            tag.setArtist(pendingTag.getArtist());
            tag.setAlbumArtist(pendingTag.getAlbumArtist());
            tag.setAlbum(pendingTag.getAlbum());
            tag.setComment(pendingTag.getComment());
            tag.setComposer(pendingTag.getComposer());
            tag.setDisc(pendingTag.getDisc());
            tag.setGenre(pendingTag.getGenre());
            tag.setGrouping(pendingTag.getGrouping());
            tag.setTitle(pendingTag.getTitle());
            tag.setTrack(pendingTag.getTrack());
            tag.setYear(pendingTag.getYear());
            tag.setSource(pendingTag.getSource());
        }
        pendingTag = null;
    }

    @Override
    public HeaderItem getHeader() {
        return header;
    }

    @Override
    public void setHeader(HeaderItem header) {
        this.header = header;
    }

    static class MediaItemViewHolder extends FlexibleViewHolder {
        String mediaPath = "";
        TextView mHeadTitle;
        TextView mTitle;
        TextView mSubtitle;
       ImageView mSamplingRateView;
        ImageView mBitrateView;
       ImageView mDurationView;
        ImageView mFileSizeView;
        ImageView mCoverArtView;
        ImageView mFileFormat;
        Context mContext;
		ImageView mNotificationView;
        ImageView mPlayerView;
       // ImageView mAudioQualityView;
        ImageView mAudioMQAView;
        TriangleLabelView mNewLabelView;

        public MediaItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            this.mContext = view.getContext();
            this.mHeadTitle = view.findViewById(R.id.header_title);
            this.mTitle = view.findViewById(R.id.item_title);
            this.mSubtitle = view.findViewById(R.id.item_subtitle);
            this.mDurationView = view.findViewById(R.id.item_duration);
            this.mSamplingRateView = view.findViewById(R.id.item_sampling_rate);
            this.mBitrateView = view.findViewById(R.id.item_bitrate);
            this.mCoverArtView = view.findViewById(R.id.item_image_coverart);
			this.mPlayerView = view.findViewById(R.id.item_player);
           // this.mAudioQualityView = view.findViewById(R.id.item_sq_icon);
            this.mAudioMQAView = view.findViewById(R.id.item_mqa_icon);
            this.mFileFormat = view.findViewById(R.id.item_file_icon);
            this.mNotificationView = view.findViewById(R.id.item_notification);
            this.mFileSizeView = view.findViewById(R.id.item_file_size);
            this.mNewLabelView = view.findViewById(R.id.item_new_label);
        }

        public Context getContext() {
            return mContext;
        }

        @Override
        public float getActivationElevation() {
            return UIUtils.dpToPx(itemView.getContext(), 4f);
        }

        @Override
        protected boolean shouldActivateViewWhileSwiping() {
            return false;//default=false
        }

        @Override
        public void scrollAnimators(@NonNull List<Animator> animators, int position, boolean isForward) {//Linear layout
            if (mAdapter.isSelected(position)) {
                AnimatorHelper.slideInFromRightAnimator(animators, itemView, mAdapter.getRecyclerView(), 0.5f);
            } else {
                AnimatorHelper.slideInFromLeftAnimator(animators,itemView,mAdapter.getRecyclerView(),0.5f);
            }
        }

        public void setSelectionAnimation(boolean selected) {
        }

        public void setAnimation(boolean selected) {
        }

        public void highlightSearch(String keyword) {
            UIUtils.highlightSearchKeywordOnTitle(mTitle, String.valueOf(mTitle.getText()), keyword);
            UIUtils.highlightSearchKeyword(mSubtitle, String.valueOf(mSubtitle.getText()), keyword);
        }

        @Override
        protected void setDragHandleView(@NonNull View view) {
            if (mAdapter.isHandleDragEnabled()) {
                view.setVisibility(View.VISIBLE);
                super.setDragHandleView(view);
            } else {
                view.setVisibility(View.GONE);
            }
        }

        @Override
        protected boolean shouldAddSelectionInActionMode() {
            return false;//default=false
        }

        public void bindNotification(MediaItem item) {
            boolean isListening = false;
            MediaItem listeningItem = null;
            if (MusicListeningService.getInstance() != null) {
                listeningItem = MusicListeningService.getInstance().getListeningSong();
                isListening = item.equals(listeningItem);
            }

          //  int borderColor = getContext().getColor(R.color.black);
          //  int hraColor = item.getSampleRateColor(getContext());
          //  int qualityColor = item.getSampleRateColor(getContext());

            if(isListening) {
                // set border
                this.itemView.setBackground(ContextCompat.getDrawable(getContext(),R.drawable.selector_item_border));
                  this.mPlayerView.setImageBitmap(AudioTagUtils.createBitmapFromDrawable(getContext(), 1, 1, MusicListeningService.getInstance().getPlayerIconDrawable(), Color.WHITE, Color.TRANSPARENT));
                  this.mPlayerView.setVisibility(View.VISIBLE);
                  mTitle.setTextColor(getContext().getColor(R.color.drawer_header_background));
            }else {
                this.itemView.setBackground(ContextCompat.getDrawable(getContext(),R.drawable.selector_item));
                 this.mPlayerView.setVisibility(View.GONE);
                mTitle.setTextColor(getContext().getColor(R.color.grey100));
            }

            // reset border
            if(item.isOnManagedDir()) {
                mNewLabelView.setVisibility(View.GONE);
            }else if(item.isOnDownloadDir()) {
                mNewLabelView.setTriangleBackgroundColorResource(R.color.material_color_red_900);
                mNewLabelView.setPrimaryTextColorResource(R.color.material_color_yellow_500);
                mNewLabelView.setSecondaryTextColorResource(R.color.material_color_yellow_100);
                mNewLabelView.setVisibility(View.VISIBLE);
            } else {
                mNewLabelView.setTriangleBackgroundColorResource(R.color.material_color_yellow_900);
                mNewLabelView.setPrimaryTextColorResource(R.color.material_color_yellow_500);
                mNewLabelView.setSecondaryTextColorResource(R.color.material_color_yellow_100);
                mNewLabelView.setVisibility(View.VISIBLE);
            }

            mNotificationView.setImageBitmap(AudioTagUtils.getSourceIcon(getContext(), item));

            // show MQA, DSDx, HRA
            Bitmap resBitmap = AudioTagUtils.getResIcon(getContext(), item);
            if(resBitmap != null) {
                this.mAudioMQAView.setVisibility(View.VISIBLE);
                this.mAudioMQAView.setImageBitmap(resBitmap);
            }else{
                this.mAudioMQAView.setVisibility(View.GONE);
            }
        }
    }

    private boolean isOnDownloadDir() {
        return getPath().indexOf("/Music/")<0;
    }

    private boolean isOnManagedDir() {
        return getTag().isManaged();
    }
}
