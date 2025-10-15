package apincer.music.core.playback;

import apincer.music.core.database.MusicTag;

public class NowPlaying {
    private MusicTag song;
    private Player player;

    public String getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(String repeatMode) {
        this.repeatMode = repeatMode;
    }

    public boolean isShuffle() {
        return shuffleMode;
    }

    public void setShuffleMode(boolean shuffleMode) {
        this.shuffleMode = shuffleMode;
    }

    private boolean shuffleMode;
    private String repeatMode;

    public String getPlayingState() {
        return playingState;
    }

    public void setPlayingState(String playingState) {
        this.playingState = playingState;
    }

    private String playingState;
    private String playingSpeed = "1";

    public NowPlaying() {
        this.playingSpeed = "1";
        this.playingState = "playing";
    }

    public long getElapsed() {
        return elapsed;
    }

    public MusicTag getSong() {
        return song;
    }

    public void setElapsed(long elapsed) {
        this.elapsed = elapsed;
    }

    private long elapsed;

    public NowPlaying(Player player, MusicTag song,long elapsed) {
        this.elapsed = elapsed;
        this.song = song;
        this.player = player;
        this.playingSpeed = "1";
        this.playingState = "playing";
    }

    public Player getPlayer() {
        return player;
    }

    public String getPlayingSpeed() {
        return playingSpeed;
    }

    public void setPlayingSpeed(String playingSpeed) {
        this.playingSpeed = playingSpeed;
    }

    public void setSong(MusicTag song) {
        this.song = song;
    }

    public void skipToNext(MusicTag tag) {
        if (song != null && player != null) {
            if (song.equals(tag)) {
                player.next();
            }
        }
    }

    public void next() {
        if (player != null) {
            player.next();
        }
    }

    public void play(MusicTag song) {
        if (song != null && player != null) {
            player.play(song);
        }
    }

    public void skipToNext() {
        if (player != null) {
            player.next();
        }
    }

    public void skipToPrevious() {
        if (player != null) {
           // player.previous();
        }
    }

    public boolean isPlaying(MusicTag tag) {
        if(song == null) return false;
        return song.equals(tag);
    }

    public boolean isLocalPlayer() {
        if(player == null) return false;

        return player instanceof ExternalPlayer;
    }
}
