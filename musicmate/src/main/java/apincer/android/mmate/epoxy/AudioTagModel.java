package apincer.android.mmate.epoxy;

import static com.airbnb.epoxy.EpoxyAttribute.Option.DoNotHash;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.airbnb.epoxy.EpoxyAttribute;
import com.airbnb.epoxy.EpoxyHolder;
import com.airbnb.epoxy.EpoxyModelClass;
import com.airbnb.epoxy.EpoxyModelWithHolder;
import com.airbnb.epoxy.OnModelClickListener;
import com.airbnb.epoxy.OnModelLongClickListener;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.ui.view.TriangleLabelView;
import apincer.android.mmate.utils.AudioTagUtils;
import apincer.android.mmate.utils.StringUtils;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.transform.RoundedCornersTransformation;

@EpoxyModelClass (layout = R.layout.view_list_item)
public abstract class AudioTagModel extends EpoxyModelWithHolder<AudioTagModel.Holder> {

    // Declare your model properties like this
    @EpoxyAttribute(DoNotHash)
    AudioTag tag;
    @EpoxyAttribute(DoNotHash)
    AudioTagController controller;
    @EpoxyAttribute(DoNotHash)
    View.OnClickListener clickListener;
    @EpoxyAttribute(DoNotHash)
    View.OnLongClickListener longClickListener;
    @EpoxyAttribute(DoNotHash)
    OnModelClickListener modelClickListener;
    @EpoxyAttribute(DoNotHash)
    OnModelLongClickListener modelLongClickListener;

    @Override
    protected Holder createNewHolder(@NonNull ViewParent parent) {
        return new Holder();
    }

    /*
    @Override
    public void bind(Holder holder) {
        // Implement this to bind the properties to the view
       // holder.button.setText(text);
       // holder.button.setOnClickListener(clickListener);
       // if(tag.getResultType() == SearchCriteria.RESULT_TYPE.ARTIST || tag.getResultType() == SearchCriteria.RESULT_TYPE.ALBUM) {
       //     bindGroupingView(holder);
       // }else {
            bindTagView(holder);
       // }
    } */

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof AudioTagModel_)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AudioTagModel_ that = (AudioTagModel_) o;
        if (((tag == null) != (that.tag == null))) {
            return false;
        }
        return tag.equals(that.tag);
    }

    @Override
    public int hashCode() {
        return tag.hashCode();
    }

    @Override
    public void bind(Holder holder) {
        // When user scrolls, this line binds the correct selection status
        holder.rootView.setActivated(controller.isSelected(tag));
        ImageLoader imageLoader = Coil.imageLoader(holder.mContext);

        // Background, when bound the first time
        AudioTag listeningItem = MusixMateApp.getPlayingSong();
        boolean isListening = tag.equals(listeningItem);

        holder.rootView.setOnClickListener(clickListener);
        holder.rootView.setOnLongClickListener(longClickListener);

        if (tag.isAudiophile()) {
            ImageRequest request = new ImageRequest.Builder(holder.mContext)
                    .data(AudioTagUtils.getAudiophileRecordsIcon(holder.mContext))
                    .crossfade(false)
                    .target(holder.mAudiophileLabelView)
                    .build();
            imageLoader.enqueue(request);
            holder.mAudiophileLabelView.setVisibility(View.VISIBLE);
        } else {
            holder.mAudiophileLabelView.setVisibility(View.GONE);
        }

        if (isListening) {
            //mTitleLayout
            // set border
            // holder.rootView.setBackground(ContextCompat.getDrawable(holder.mContext,R.drawable.selector_item_border));
            holder.mPlayerView.setImageDrawable(MusixMateApp.getPlayerInfo().getPlayerIconDrawable());
            holder.mPlayerView.setVisibility(View.VISIBLE);
           // holder.mTitleLayout.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_item_now_playing_background));
           // holder.mCoverArtFrame.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_item_now_playing_background));
           // holder.mTitle.setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC);
            //         holder.mTitle.setTextColor(holder.mContext.getColor(R.color.fab_listening_background));
        } else {
            //  holder.rootView.setBackground(ContextCompat.getDrawable(holder.mContext,R.drawable.selector_item));
            holder.mPlayerView.setVisibility(View.GONE);
            //  holder.mTitle.setTextColor(holder.mContext.getColor(R.color.grey100));
           // holder.mTitleLayout.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_item_background));
           // holder.mCoverArtFrame.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_item_background));
           // holder.mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        }

        // download label
        if (tag.isManaged()) {
            holder.mNewLabelView.setVisibility(View.GONE);
        } else if (AudioTagUtils.isOnDownloadDir(tag)) {
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
/***
 mNotificationView.setImageBitmap(AudioTagUtils.getSourceIcon(getContext(), item));
 */
        // Show AlbumArt
        ImageRequest request = new ImageRequest.Builder(holder.mContext)
                .data(AudioTagUtils.getCoverArt(holder.mContext, tag))
                // .size(800, 800)
                .crossfade(true)
                .target(holder.mCoverArtView)
                .transformations(new RoundedCornersTransformation(8, 8, 8, 8))
                .build();
        imageLoader.enqueue(request);

        // show enc i.e. PCM, MQA, DSD
        request = new ImageRequest.Builder(holder.mContext)
                .data(AudioTagUtils.getEncResolutionIcon(holder.mContext, tag))
                .crossfade(false)
                .target(holder.mAudioHiResView)
                .build();
        imageLoader.enqueue(request);

        // Loudness
        if(!tag.isDSD() && !StringUtils.isEmpty(tag.getLoudnessIntegrated())) {
           /* request = new ImageRequest.Builder(holder.mContext)
                    .data(AudioTagUtils.getCachedLoudnessIcon(holder.mContext, tag))
                    .crossfade(false)
                    .target(holder.mAudioLoudnessView)
                    .build();
            imageLoader.enqueue(request);*/
            holder.mAudioLoudnessView.setImageBitmap(AudioTagUtils.createLoudnessIcon(holder.mContext, tag));
            holder.mAudioLoudnessView.setVisibility(View.VISIBLE);
        }else {
            holder.mAudioLoudnessView.setVisibility(View.GONE);
        }

            holder.mTitle.setText(AudioTagUtils.getFormattedTitle(holder.mContext, tag));
            holder.mSubtitle.setText(AudioTagUtils.getFormattedSubtitle(tag));

        Drawable resolutionBackground = AudioTagUtils.getResolutionBackground(holder.mContext, tag);

        // file type
        holder.mFileTypeView.setText(tag.getAudioEncoding());
        holder.mFileTypeView.setBackground(resolutionBackground);

        // duration
        holder.mDurationView.setText(tag.getAudioDurationAsString());
       // holder.mDurationView.setBackground(resolutionBackground);

        // file size
        holder.mFileSizeView.setText(StringUtils.formatStorageSize(tag.getFileSize()));
            Bitmap srcBmp = AudioTagUtils.getSourceIcon(holder.mContext, tag.getSource());
            if(srcBmp!=null) {
                holder.mFileSourceView.setImageBitmap(srcBmp);
                holder.mFileSourceView.setVisibility(View.VISIBLE);
            }else {
                holder.mFileSourceView.setVisibility(View.GONE);
            }
    }

    @Override
    protected int getDefaultLayout() {
        return R.layout.view_list_item;
    }

    public static class Holder extends EpoxyHolder {
        View rootView;
        View mCoverArtFrame;
        View mTitleLayout;
        TextView mTitle;
        TextView mSubtitle;
       // TextView mBitPerSamplingView;
       // TextView mSamplingRateView;
       // ImageView mBitrateView;
        TextView mDurationView;
        TextView mFileSizeView;
        ImageView mCoverArtView;
        ImageView mFileSourceView;
       // ImageView mFileTypeView;
        TextView mFileTypeView;
        Context mContext;
        //ImageView mNotificationView;
        ImageView mPlayerView;
        // ImageView mAudioQualityView;
        ImageView mAudioHiResView;
        ImageView mAudioLoudnessView;
        TriangleLabelView mNewLabelView;
        ImageView mAudiophileLabelView;
        //ImageView mAudiophileLabelView;

        @Override
        protected void bindView(View view) {
            rootView = view;
            this.mContext = view.getContext();
            this.mCoverArtFrame = view.findViewById(R.id.item_imageFrame);
            this.mTitleLayout = view.findViewById(R.id.item_title_layout);
            this.mTitle = view.findViewById(R.id.item_title);
            this.mSubtitle = view.findViewById(R.id.item_subtitle);
            this.mDurationView = view.findViewById(R.id.item_duration);
           // this.mBitPerSamplingView = view.findViewById(R.id.item_bit_per_sampling);
           // this.mSamplingRateView = view.findViewById(R.id.item_sampling_rate);
           // this.mBitrateView = view.findViewById(R.id.item_bitrate);
            this.mCoverArtView = view.findViewById(R.id.item_image_coverart);
            this.mPlayerView = view.findViewById(R.id.item_player);
            // this.mAudioQualityView = view.findViewById(R.id.item_sq_icon);
            this.mAudioHiResView = view.findViewById(R.id.item_hires_icon);
            this.mFileSourceView = view.findViewById(R.id.item_src_icon);
            this.mFileTypeView = view.findViewById(R.id.item_type_label);

           // this.mNotificationView = view.findViewById(R.id.item_notification);
            this.mFileSizeView = view.findViewById(R.id.item_file_size);
            this.mNewLabelView = view.findViewById(R.id.item_new_label);
            this.mAudiophileLabelView = view.findViewById(R.id.item_audiophile_label);
            this.mAudioLoudnessView = view.findViewById(R.id.item_loudness_icon);
        }
    }
}