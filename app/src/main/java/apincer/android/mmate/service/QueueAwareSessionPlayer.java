package apincer.android.mmate.service;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.media3.common.ForwardingSimpleBasePlayer;
import androidx.media3.common.Player;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Player handed to the MediaLibrarySession (notification, lock screen, headset, Bluetooth).
 * ExoPlayer only holds the playing track and at most one gapless follower, so its own
 * Next/Previous cannot follow the MusicMate queue. This wrapper always offers Next/Previous
 * and routes them to the service queue; everything else passes through to ExoPlayer.
 */
final class QueueAwareSessionPlayer extends ForwardingSimpleBasePlayer {

    private final Runnable skipToNext;
    private final Runnable skipToPrevious;
    private final Handler handler;

    QueueAwareSessionPlayer(Player player, Runnable skipToNext, Runnable skipToPrevious) {
        super(player);
        this.skipToNext = skipToNext;
        this.skipToPrevious = skipToPrevious;
        this.handler = new Handler(player.getApplicationLooper());
    }

    /** Runs a queue skip after the seek call returns, since the skip replaces ExoPlayer's playlist. */
    private ListenableFuture<?> runQueueSkip(Runnable skip) {
        handler.post(skip);
        return Futures.immediateVoidFuture();
    }

    @NonNull
    @Override
    protected State getState() {
        State state = super.getState();
        return state.buildUpon()
                .setAvailableCommands(state.availableCommands.buildUpon()
                        .addAll(COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                                COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build())
                .build();
    }

    @NonNull
    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, int seekCommand) {
        switch (seekCommand) {
            case COMMAND_SEEK_TO_NEXT:
            case COMMAND_SEEK_TO_NEXT_MEDIA_ITEM:
                return runQueueSkip(skipToNext);
            case COMMAND_SEEK_TO_PREVIOUS:
                // Standard behaviour: past the threshold, Previous restarts the current track
                if (getPlayer().getCurrentPosition() > getPlayer().getMaxSeekToPreviousPosition()) {
                    return super.handleSeek(getPlayer().getCurrentMediaItemIndex(), 0,
                            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
                }
                return runQueueSkip(skipToPrevious);
            case COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM:
                return runQueueSkip(skipToPrevious);
            default:
                return super.handleSeek(mediaItemIndex, positionMs, seekCommand);
        }
    }
}
