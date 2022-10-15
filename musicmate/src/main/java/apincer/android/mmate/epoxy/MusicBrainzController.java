package apincer.android.mmate.epoxy;

import android.view.View;

import com.airbnb.epoxy.EpoxyViewHolder;
import com.airbnb.epoxy.TypedEpoxyController;

import java.util.List;

import apincer.android.mmate.objectbox.MusicTag;

public class MusicBrainzController extends TypedEpoxyController<List<MusicTag>> {
    View.OnClickListener clickListener;
    @Override
    protected void buildModels(List<MusicTag> audioTags) {
        for (MusicTag tag : audioTags) {
            new MusicBrainzModel_()
                    .id(tag.getId())
                    .tag(tag)
                    .clickListener(clickListener)
                    .addTo(this);
        }
    }

    public MusicBrainzController(View.OnClickListener clickListener) {
        super();
        this.clickListener = clickListener;
    }

    public MusicTag getAudioTag(EpoxyViewHolder holder) {
        return ((MusicBrainzModel_)holder.getModel()).tag();
    }
}
