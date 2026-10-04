package apincer.music.core.service.spi;

import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.server.spi.MediaServerHub;

public interface MusicMateServiceBinder {

    PlaybackService getPlaybackService();

    MediaServerHub getMediaServerHub();
}
