package apincer.android.mmate.item;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.Serializable;
import java.util.List;

import apincer.android.mmate.R;
import apincer.android.mmate.fs.MusicbrainzCoverArtProvider;
import apincer.android.mmate.utils.UIUtils;
import coil.Coil;
import coil.request.LoadRequest;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.viewholders.FlexibleViewHolder;

/**
 * MusicBrainz Song (recording) data
 */
public class RecordingItem extends AbstractFlexibleItem<RecordingItem.RecordingItemViewHolder> implements Serializable {

    static class RecordingItemViewHolder extends FlexibleViewHolder {
        ImageView mImage;
        TextView mTitle;
        TextView mSubtitle;
        TextView mSubtitle2;
        Context mContext;

        public RecordingItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            this.mContext = view.getContext();
            this.mImage = view.findViewById(R.id.image);
            this.mTitle = view.findViewById(R.id.title);
            this.mSubtitle = view.findViewById(R.id.subtitle1);
            this.mSubtitle2 = view.findViewById(R.id.subtitle2);
         }

        public Context getContext() {
            return mContext;
        }

        @Override
        public float getActivationElevation() {
            return UIUtils.dpToPx(itemView.getContext(), 4f);
        }
    }

    /**
     * The MusicBrainz ID of the release
     */
    public String id;
    public String title;
    public String artist;
    public String artistId;
    public String album;
    public String albumId;
    public String genre;
    public String year;

    @Override
    public boolean equals(Object inObject) {
        if (inObject instanceof RecordingItem) {
            RecordingItem inItem = (RecordingItem) inObject;
            return this.id.equals(inItem.id);
        }
        return false;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.view_list_item_musicbrainz;
    }

    @Override
    public RecordingItem.RecordingItemViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new RecordingItem.RecordingItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter adapter, RecordingItem.RecordingItemViewHolder holder, int position, List payloads) {
        holder.mTitle.setText(title);
        holder.mSubtitle.setText(artist);
        holder.mSubtitle2.setText(album);
        LoadRequest request = LoadRequest.builder(holder.mContext)
                .data(MusicbrainzCoverArtProvider.getUriForMediaItem(RecordingItem.this))
                .target(holder.mImage)
                .placeholder(R.drawable.progress)
                .error(R.drawable.ic_broken_image_black_24dp)
                .size(240, 240)
                .build();
        Coil.execute(request);
    }

    @Override
    public String toString() {
        return "RecordingItem{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", albumId='" + albumId + '\'' +
                ", year='" + year + '\'' +
                '}';
    }

}