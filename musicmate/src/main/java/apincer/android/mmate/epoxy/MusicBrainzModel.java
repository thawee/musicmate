package apincer.android.mmate.epoxy;

import static com.airbnb.epoxy.EpoxyAttribute.Option.DoNotHash;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.airbnb.epoxy.EpoxyAttribute;
import com.airbnb.epoxy.EpoxyHolder;
import com.airbnb.epoxy.EpoxyModelClass;
import com.airbnb.epoxy.EpoxyModelWithHolder;

import apincer.android.mmate.R;
import apincer.android.mmate.fs.MusicbrainzCoverArtProvider;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.transform.RoundedCornersTransformation;

@SuppressLint("NonConstantResourceId")
@EpoxyModelClass(layout = R.layout.view_list_item_musicbrainz)
public abstract class MusicBrainzModel extends EpoxyModelWithHolder<MusicBrainzModel.Holder> {
    @EpoxyAttribute(DoNotHash)
    MusicTag tag;
    @EpoxyAttribute(DoNotHash)
    View.OnClickListener clickListener;

    @Override
    protected Holder createNewHolder(@NonNull ViewParent parent) {
        return new Holder();
    }

    @Override
    protected int getDefaultLayout() {
        return R.layout.view_list_item_musicbrainz;
    }

    @Override
    public void bind(MusicBrainzModel.Holder holder) {
        holder.rootView.setOnClickListener(clickListener);
        holder.mTitle.setText(tag.getTitle());
        holder.mSubtitle1.setText(MusicTagUtils.getFormattedSubtitle(tag));
        ImageLoader imageLoader = Coil.imageLoader(holder.mContext);
        ImageRequest request = new ImageRequest.Builder(holder.mContext)
                .data(MusicbrainzCoverArtProvider.getUriForMediaItem(tag))
                .crossfade(true)
                .target(holder.mCoverArtView)
                .transformations(new RoundedCornersTransformation(12,12,12,12))
                .build();
        imageLoader.enqueue(request);

    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MusicBrainzModel_)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        MusicBrainzModel_ that = (MusicBrainzModel_) o;
        if (((tag == null) != (that.tag == null))) {
            return false;
        }
        assert tag != null;
        return tag.equals(that.tag);
    }

    @Override
    public int hashCode() {
        return tag.hashCode();
    }

    public static class Holder extends EpoxyHolder {
        View rootView;
        TextView mTitle;
        TextView mSubtitle1;
        TextView mSubtitle2;
        ImageView mCoverArtView;
        Context mContext;
        @Override
        protected void bindView(@NonNull View itemView) {
            rootView = itemView;
            this.mContext = itemView.getContext();
            this.mCoverArtView = itemView.findViewById(R.id.image);
            this.mTitle = itemView.findViewById(R.id.title);
            this.mSubtitle1 = itemView.findViewById(R.id.subtitle1);
            this.mSubtitle2 = itemView.findViewById(R.id.subtitle2);
        }
    }
}
