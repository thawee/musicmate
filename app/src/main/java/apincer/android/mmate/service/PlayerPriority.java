package apincer.android.mmate.service;

import apincer.music.core.playback.DMRPlayer;
import apincer.music.core.playback.ExternalAndroidPlayer;
import apincer.music.core.playback.spi.PlaybackTarget;

/**
 * Which playback target wins when one is not chosen by the listener (user, 2026-10-02). The
 * listener's choice always wins (not here). An automatic switch never interrupts a target that
 * is playing, and an idle target never blocks one that starts. The order - a DLNA stream (a TV or
 * browser pulling from MusicMate, or a renderer driven by another app) > the DLNA renderer the
 * listener chose > local playback > an external player app - decides between two starting at
 * once and the startup default.
 */
final class PlayerPriority {
    static final int EXTERNAL_APP = 1;
    static final int LOCAL = 2;
    static final int RENDERER = 3;
    static final int STREAM = 4;

    private PlayerPriority() {
    }

    /** @param drivenRenderer true when the target is the DLNA renderer MusicMate drives */
    static int rank(PlaybackTarget target, boolean drivenRenderer) {
        if (target == null) return 0;
        if (target instanceof DMRPlayer && drivenRenderer) return RENDERER;
        if (target.isStreaming()) return STREAM; // web streams and renderers MusicMate only follows
        if (ExternalAndroidPlayer.LOCAL_TARGET_ID.equals(target.getTargetId())) return LOCAL;
        return EXTERNAL_APP;
    }

    /**
     * May an automatic (not listener-chosen) switch replace the current target? An idle target
     * never blocks one that starts (activity beats idleness); a playing one is never interrupted,
     * except that when two start at once (the current one took over automatically moments ago)
     * the higher rank wins.
     */
    static boolean mayTakeOver(int currentRank, boolean currentPlaying, boolean currentJustTookOver, int newRank) {
        if (!currentPlaying) return true;
        return currentJustTookOver && newRank > currentRank;
    }
}
