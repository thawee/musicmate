package apincer.music.core.repository.spi;

import apincer.music.core.model.Track;

public interface TrackProcessor {
    void process(Track track);
}
