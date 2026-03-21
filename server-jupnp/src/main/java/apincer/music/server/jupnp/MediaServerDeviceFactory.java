package apincer.music.server.jupnp;
import android.content.Context;

import org.jupnp.model.ValidationException;
import org.jupnp.model.meta.LocalDevice;

import apincer.music.core.repository.TagRepository;

public class MediaServerDeviceFactory {

    public static MediaServerDevice create(Context context, TagRepository tagRepos) {
        try {
            return new MediaServerDevice(context, tagRepos);
        } catch (ValidationException e) {
            return null;
        }
    }
}