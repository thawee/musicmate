package apincer.android.mmate.epoxy;

import static com.airbnb.epoxy.EpoxyAttribute.Option.DoNotHash;

import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
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

import java.util.Locale;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.ui.view.TriangleLabelView;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.transform.RoundedCornersTransformation;

@SuppressLint("NonConstantResourceId")
@EpoxyModelClass (layout = R.layout.view_list_item)
public abstract class MusicTagModel extends EpoxyModelWithHolder<MusicTagModel.Holder> {

    // Declare your model properties like this
    @EpoxyAttribute(DoNotHash)
    MusicTag tag;
    @EpoxyAttribute(DoNotHash)
    MusicTagController controller;
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

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MusicTagModel_)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        MusicTagModel_ that = (MusicTagModel_) o;
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
       // if("Come on Over".equalsIgnoreCase(tag.getTitle())) {
       //     tag.getTrack();
       // }

        // Background, when bound the first time
        MusicTag listeningItem = MusixMateApp.getPlayingSong();
        boolean isListening = tag.equals(listeningItem);

        holder.rootView.setOnClickListener(clickListener);
        holder.rootView.setOnLongClickListener(longClickListener);

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

        // Show AlbumArt
        ImageRequest request = new ImageRequest.Builder(holder.mContext)
                .data(MusicTagUtils.getCoverArt(holder.mContext, tag))
                // .size(800, 800)
                .crossfade(true)
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

        // Loudness
       // if(!tag.isDSD()) {
           // holder.mAudioLoudnessView.setImageBitmap(MusicTagUtils.createLoudnessIcon(holder.mContext, tag));
           // holder.mAudioLoudnessView.setVisibility(View.VISIBLE);
       //     holder.mTrackReplayGainView.setVisibility(View.VISIBLE);
       // }else {
           // holder.mAudioLoudnessView.setVisibility(View.GONE);
       //     holder.mTrackReplayGainView.setVisibility(View.GONE);
       // }

        holder.mTitle.setText(MusicTagUtils.getFormattedTitle(holder.mContext, tag));
        holder.mSubtitle.setText(MusicTagUtils.getFormattedSubtitle(tag));

        // file format
        Drawable resolutionBackground = MusicTagUtils.getResolutionBackground(holder.mContext, tag);
        holder.mFileTypeView.setText(trimToEmpty(tag.getFileFormat()).toUpperCase(Locale.US));
        holder.mFileTypeView.setBackground(resolutionBackground);

        holder.mFileBrokenView.setVisibility(MusicTagUtils.isFileCouldBroken(tag)?View.VISIBLE:View.GONE);

        // Dynamic Range
        resolutionBackground = MusicTagUtils.getResolutionBackground(holder.mContext, tag);
        if(tag.getTrackDR()==0.00) {
            holder.mDynamicRange.setText(" - ");
        }else {
            holder.mDynamicRange.setText(String.format(Locale.US, "DR%.0f", tag.getTrackDR()));
        }
        holder.mDynamicRange.setBackground(resolutionBackground);

        resolutionBackground = MusicTagUtils.getResolutionBackground(holder.mContext, tag);
        if(tag.getTrackRG() ==0.00) {
            holder.mTrackReplayGainView.setText(" - ");
        }else {
            holder.mTrackReplayGainView.setText(String.format(Locale.US, "G%.2f", tag.getTrackRG()));
        }
        holder.mTrackReplayGainView.setBackground(resolutionBackground);

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
        TextView mTrackReplayGainView;

        @Override
        protected void bindView(View view) {
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
            //this.mAudioLoudnessView = view.findViewById(R.id.item_loudness_icon);
            this.mTrackReplayGainView = view.findViewById(R.id.item_track_rg_icon);
        }
    }
}