package apincer.android.mmate.epoxy;

import static com.airbnb.epoxy.EpoxyAttribute.Option.DoNotHash;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.*;
import androidx.core.content.ContextCompat;

import com.airbnb.epoxy.EpoxyAttribute;
import com.airbnb.epoxy.EpoxyHolder;
import com.airbnb.epoxy.EpoxyModelClass;
import com.airbnb.epoxy.EpoxyModelWithHolder;
import com.airbnb.epoxy.OnModelClickListener;
import com.airbnb.epoxy.OnModelLongClickListener;

import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.BroadcastHelper;
import apincer.android.mmate.fs.EmbedCoverArtProvider;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.SearchCriteria;
//import apincer.android.mmate.broadcast.MusicListeningService;
import apincer.android.mmate.ui.view.TriangleLabelView;
import apincer.android.mmate.utils.AudioTagUtils;
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

    @Override
    public void bind(Holder holder) {
        // Implement this to bind the properties to the view
       // holder.button.setText(text);
       // holder.button.setOnClickListener(clickListener);
        if(tag.getResultType() == SearchCriteria.RESULT_TYPE.ARTIST || tag.getResultType() == SearchCriteria.RESULT_TYPE.ALBUM) {
            bindGroupingView(holder);
        }else {
            bindTagView(holder);
        }
    }

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

    private void bindTagView(Holder holder) {
        // When user scrolls, this line binds the correct selection status
         holder.rootView.setActivated(controller.isSelected(tag));

        // Background, when bound the first time
        boolean isListening = false;
        AudioTag listeningItem = null;
       // if (MusicListeningService.getInstance() != null) {
            listeningItem = BroadcastHelper.getPlayingSong(); // MusicListeningService.getInstance().getPlayingSong();
            isListening = tag.equals(listeningItem);
       // }

        holder.rootView.setOnClickListener(clickListener);
        holder.rootView.setOnLongClickListener(longClickListener);

        if(tag.isAudiophile()) {
            holder.mAudiophileLabelView.setVisibility(View.VISIBLE);
          //  holder.mTitleLayout.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_audiophile_background));
         //   holder.mCoverArtFrame.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_audiophile_background));
        }else {
            holder.mAudiophileLabelView.setVisibility(View.GONE);
          //  holder.mTitleLayout.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_item_background));
          //  holder.mCoverArtFrame.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_item_background));
        }

        if(isListening) {
            //mTitleLayout
            // set border
           // holder.rootView.setBackground(ContextCompat.getDrawable(holder.mContext,R.drawable.selector_item_border));
           // this.mPlayerView.setImageBitmap(AudioTagUtils.createBitmapFromDrawable(getContext(), 1, 1, MusicListeningService.getInstance().getPlayerIconDrawable(), Color.WHITE, Color.TRANSPARENT));
           // this.mPlayerView.setVisibility(View.VISIBLE);
              holder.mTitleLayout.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_item_now_playing_background));
              holder.mCoverArtFrame.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_item_now_playing_background));
   //         holder.mTitle.setTextColor(holder.mContext.getColor(R.color.fab_listening_background));
        }else {
          //  holder.rootView.setBackground(ContextCompat.getDrawable(holder.mContext,R.drawable.selector_item));
           // this.mPlayerView.setVisibility(View.GONE);
          //  holder.mTitle.setTextColor(holder.mContext.getColor(R.color.grey100));
            holder.mTitleLayout.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_item_background));
            holder.mCoverArtFrame.setBackground(ContextCompat.getDrawable(holder.mContext, R.drawable.shape_item_background));
        }

        // download label
        if(tag.isManaged()) {
            holder.mNewLabelView.setVisibility(View.GONE);
        }else if(AudioTagUtils.isOnDownloadDir(tag)) {
            holder.mNewLabelView.setTriangleBackgroundColorResource(R.color.material_color_red_900);
           // holder.mNewLabelView.setPrimaryTextColorResource(R.color.material_color_yellow_500);
            holder.mNewLabelView.setPrimaryTextColorResource(R.color.grey200);
            holder.mNewLabelView.setSecondaryTextColorResource(R.color.material_color_yellow_100);
            holder.mNewLabelView.setVisibility(View.VISIBLE);
        } else {
            //holder.mNewLabelView.setTriangleBackgroundColorResource(R.color.material_color_yellow_900);
            holder.mNewLabelView.setTriangleBackgroundColorResource(R.color.material_color_yellow_200);
           // holder.mNewLabelView.setPrimaryTextColorResource(R.color.material_color_yellow_500);
            holder.mNewLabelView.setPrimaryTextColorResource(R.color.grey800);
            holder.mNewLabelView.setSecondaryTextColorResource(R.color.material_color_yellow_100);
            holder.mNewLabelView.setVisibility(View.VISIBLE);
        }
/***
        mNotificationView.setImageBitmap(AudioTagUtils.getSourceIcon(getContext(), item));
*/
        // Show AlbumArt
        ImageLoader imageLoader = Coil.imageLoader(holder.mContext);
        ImageRequest request = new ImageRequest.Builder(holder.mContext)
                .data(EmbedCoverArtProvider.getUriForMediaItem(tag))
               // .size(800, 800)
                .crossfade(true)
                .target(holder.mCoverArtView)
                .transformations(new RoundedCornersTransformation(12,12,12,12))
                .build();
        imageLoader.enqueue(request);

        // show file type i.e. DxD, FLAC, ALAC, MP3
        Bitmap typeBitmap = AudioTagUtils.getFileFormatIcon(holder.mContext, tag);
        if(typeBitmap != null) {
            holder.mFileTypeView.setVisibility(View.VISIBLE);
            holder.mFileTypeView.setImageBitmap(typeBitmap);
        }else{
            holder.mFileTypeView.setVisibility(View.GONE);
        }

        // show enc i.e. MQA
        Bitmap resBitmap = AudioTagUtils.getResIcon(holder.mContext, tag);
        if(resBitmap != null) {
            holder.mAudioENCView.setVisibility(View.VISIBLE);
            holder.mAudioENCView.setImageBitmap(resBitmap);
        }else{
            holder.mAudioENCView.setVisibility(View.GONE);
        }
        /* Bitmap resBitmap = AudioTagUtils.getResIcon(holder.mContext, tag);
        if(resBitmap != null) {
            this.mAudioMQAView.setVisibility(View.VISIBLE);
            this.mAudioMQAView.setImageBitmap(resBitmap);
        }else{
            this.mAudioMQAView.setVisibility(View.GONE);
        } */
        /*
        if (adapter.isSelectAll() || adapter.isLastItemInActionMode()) {
            // Consume the Animation
            holder.setSelectionAnimation(adapter.isSelected(position));
        } else {
            // Display the current flip status
            holder.setAnimation(adapter.isSelected(position));
        } */

            holder.mTitle.setText(AudioTagUtils.getFormattedTitle(holder.mContext, tag));
            holder.mSubtitle.setText(AudioTagUtils.getFormattedSubtitle(tag));

            // int borderColor = holder.getContext().getColor(R.color.black);
            //  int textColor = holder.getContext().getColor(R.color.black);
            //  int qualityColor = getSampleRateColor(holder.getContext());

        if(tag.isMQA()){
            Bitmap bmp = AudioTagUtils.getMQASampleRateIcon(holder.mContext,tag);
            if(bmp != null) {
                holder.mBitrateView.setVisibility(View.VISIBLE);
                holder.mBitrateView.setImageBitmap(bmp);
            }else {
                holder.mBitrateView.setVisibility(View.GONE);
            }
        }else if(tag.isDSD()) {
                holder.mBitrateView.setVisibility(View.VISIBLE);
                //holder.mBitrateView.setImageBitmap(AudioTagUtils.getBitRateIcon(holder.mContext,tag));
            holder.mBitrateView.setImageBitmap(AudioTagUtils.getDSDSampleRateIcon(holder.mContext,tag));
        }else if(!tag.isLossless()) { // && !AudioTagUtils.isHiRes(tag)) { // && !(metadata.isMQA() || metadata.isPCMHiRes192() || metadata.isPCMHiRes88_96() || metadata.isPCMHiRes44())) {
            holder.mBitrateView.setVisibility(View.VISIBLE);
            //holder.mBitrate.setText(StringUtils.getFormatedAudioBitRate(metadata.getAudioBitRate()));
            // holder.mBitrateView.setImageBitmap(MediaItemUtils.createBitmapFromText(holder.getContext(), 120, 32, StringUtils.getFormatedAudioBitRate(metadata.getAudioBitRate()), textColor,borderColor, qualityColor));
            holder.mBitrateView.setImageBitmap(AudioTagUtils.getBitRateIcon(holder.mContext,tag));
        }else {
                holder.mBitrateView.setVisibility(View.GONE);
            }

            // holder.mSamplingRateView.setImageBitmap(MediaItemUtils.createBitmapFromText(holder.getContext(), Constants.INFO_SAMPLE_RATE_WIDTH, 32, getMetadata().getAudioBitCountAndSampleRate(), textColor,borderColor, qualityColor));
            holder.mSamplingRateView.setImageBitmap(AudioTagUtils.getSampleRateIcon(holder.mContext, tag));
            // holder.mDurationView.setImageBitmap(MediaItemUtils.createBitmapFromText(holder.getContext(), 80, 32, getMetadata().getAudioDurationAsString(), textColor,borderColor, qualityColor));
            holder.mDurationView.setImageBitmap(AudioTagUtils.getDurationIcon(holder.mContext, tag));
            // holder.mFileSizeView.setImageBitmap(MediaItemUtils.createBitmapFromText(holder.getContext(), 100, 32, getMetadata().getMediaSize(), textColor,borderColor, qualityColor));
            holder.mFileSizeView.setImageBitmap(AudioTagUtils.getFileSizeIcon(holder.mContext, tag));

            // holder.mFileFormat.setImageBitmap(MediaItemUtils.createBitmapFromText(holder.getContext(), 60, 32, getMetadata().getAudioEncoding(), Color.WHITE ,Color.WHITE, holder.getContext().getColor(getEncodingColorId())));
           // holder.mFileFormat.setImageBitmap(AudioTagUtils.getFileFormatIcon(holder.mContext, tag));
            Bitmap srcBmp = AudioTagUtils.getSourceIcon(holder.mContext, tag);
            if(srcBmp!=null) {
                holder.mFileSourceView.setImageBitmap(srcBmp);
                holder.mFileSourceView.setVisibility(View.VISIBLE);
            }else {
                holder.mFileSourceView.setVisibility(View.GONE);
            }
    }

    private void bindGroupingView(Holder holder) {
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
        ImageView mSamplingRateView;
        ImageView mBitrateView;
        ImageView mDurationView;
        ImageView mFileSizeView;
        ImageView mCoverArtView;
        ImageView mFileSourceView;
        ImageView mFileTypeView;
        Context mContext;
        //ImageView mNotificationView;
        ImageView mPlayerView;
        // ImageView mAudioQualityView;
        ImageView mAudioENCView;
        TriangleLabelView mNewLabelView;
        View mAudiophileLabelView;

        @Override
        protected void bindView(View view) {
            rootView = view;
            this.mContext = view.getContext();
            this.mCoverArtFrame = view.findViewById(R.id.item_imageFrame);
            this.mTitleLayout = view.findViewById(R.id.item_title_layout);
            this.mTitle = view.findViewById(R.id.item_title);
            this.mSubtitle = view.findViewById(R.id.item_subtitle);
            this.mDurationView = view.findViewById(R.id.item_duration);
            this.mSamplingRateView = view.findViewById(R.id.item_sampling_rate);
            this.mBitrateView = view.findViewById(R.id.item_bitrate);
            this.mCoverArtView = view.findViewById(R.id.item_image_coverart);
            this.mPlayerView = view.findViewById(R.id.item_player);
            // this.mAudioQualityView = view.findViewById(R.id.item_sq_icon);
            this.mAudioENCView = view.findViewById(R.id.item_enc_icon);
            this.mFileSourceView = view.findViewById(R.id.item_src_icon);
            this.mFileTypeView = view.findViewById(R.id.item_type_icon);

           // this.mNotificationView = view.findViewById(R.id.item_notification);
            this.mFileSizeView = view.findViewById(R.id.item_file_size);
            this.mNewLabelView = view.findViewById(R.id.item_new_label);
            this.mAudiophileLabelView = view.findViewById(R.id.item_audiophile_label);
        }
    }
}