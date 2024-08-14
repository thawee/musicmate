package apincer.android.mmate.notification;

import org.greenrobot.eventbus.EventBus;

import apincer.android.mmate.repository.MusicTag;

public class AudioTagPlayingEvent {
    public final MusicTag playingSong;

    public AudioTagPlayingEvent(MusicTag playingSong) {
        this.playingSong = playingSong;
    }
    public MusicTag getPlayingSong() {
        return playingSong;
    }

    public static void publishPlayingSong(MusicTag playingSong) {
        AudioTagPlayingEvent message = new AudioTagPlayingEvent(playingSong);
        EventBus.getDefault().postSticky(message);
    }
}