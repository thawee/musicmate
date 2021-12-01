package apincer.android.mmate.epoxy;

import android.view.View;

import com.airbnb.epoxy.EpoxyViewHolder;
import com.airbnb.epoxy.TypedEpoxyController;

import java.util.List;

import apincer.android.mmate.objectbox.AudioTag;

public class MusicBrainzController extends TypedEpoxyController<List<AudioTag>> {
    View.OnClickListener clickListener;
    @Override
    protected void buildModels(List<AudioTag> audioTags) {
        for (AudioTag tag : audioTags) {
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

    public AudioTag getAudioTag(EpoxyViewHolder holder) {
        return ((MusicBrainzModel_)holder.getModel()).tag();
    }
}
