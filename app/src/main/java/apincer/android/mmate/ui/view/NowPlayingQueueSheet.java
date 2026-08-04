package apincer.android.mmate.ui.view;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.SeekBar;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.R;
import apincer.android.mmate.coil3.CoverartFetcher;
import apincer.android.mmate.service.MusicMateServiceImpl;
import apincer.music.core.model.Track;
import apincer.music.core.playback.PlaybackState;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.repository.QueueManager;
import apincer.music.core.utils.PlayerNameUtils;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;

/**
 * Premium bottom sheet showing the current Now Playing track details
 * plus the full queue with Clear Queue and Stop actions.
 */
public class NowPlayingQueueSheet extends BottomSheetDialogFragment {

    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound = false;
    private AutoCloseable songSubscription;
    private AutoCloseable stateSubscription;

    // ── Service binding ──────────────────────────────────────────────────────

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicMateServiceImpl.MusicMateServiceImplBinder binder =
                    (MusicMateServiceImpl.MusicMateServiceImplBinder) service;
            playbackService = binder.getPlaybackService();
            isPlaybackServiceBound = true;
            subscribeToServiceUpdates();
            populateSheet(getView());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            unsubscribeFromServiceUpdates();
            isPlaybackServiceBound = false;
            playbackService = null;
        }
    };

    private void subscribeToServiceUpdates() {
        unsubscribeFromServiceUpdates();

        if (playbackService == null) return;

        // 1. Subscribe to song changes -> full sheet re-population
        songSubscription = playbackService.subscribeNowPlayingSong(
                optTrack -> {
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> populateSheet(getView()));
                    }
                },
                err -> android.util.Log.e("NowPlayingQueueSheet", "Error observing song change", err)
        );

        // 2. Subscribe to playback state/position updates -> seekbar & timer update
        stateSubscription = playbackService.subscribePlaybackState(
                state -> {
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> updatePlaybackProgress(getView(), state));
                    }
                },
                err -> android.util.Log.e("NowPlayingQueueSheet", "Error observing playback state", err)
        );
    }

    private void unsubscribeFromServiceUpdates() {
        if (songSubscription != null) {
            try { songSubscription.close(); } catch (Exception ignored) {}
            songSubscription = null;
        }
        if (stateSubscription != null) {
            try { stateSubscription.close(); } catch (Exception ignored) {}
            stateSubscription = null;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(getContext(), MusicMateServiceImpl.class);
        if (getContext() != null) {
            getContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }

        // Limit bottom sheet height to 2/3 of device screen height
        if (getDialog() instanceof com.google.android.material.bottomsheet.BottomSheetDialog) {
            View bottomSheet = ((com.google.android.material.bottomsheet.BottomSheetDialog) getDialog())
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);

                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int maxSheetHeight = (screenHeight * 2) / 3;

                ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.height = maxSheetHeight;
                    bottomSheet.setLayoutParams(layoutParams);
                }

                behavior.setMaxHeight(maxSheetHeight);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        unsubscribeFromServiceUpdates();
        if (isPlaybackServiceBound && getContext() != null) {
            getContext().unbindService(serviceConnection);
            isPlaybackServiceBound = false;
            playbackService = null;
        }
    }

    // ── Inflate ──────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_now_playing_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Close button in header
        View closeBtn = view.findViewById(R.id.btn_close_queue_sheet);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dismiss());
        }

        // Signal Path icon button in header -> Open SignalPathBottomSheet
        View signalPathBtn = view.findViewById(R.id.btn_open_signal_path);
        if (signalPathBtn != null) {
            signalPathBtn.setOnClickListener(v -> {
                dismiss();
                if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                    ((apincer.android.mmate.ui.MainActivity) getActivity()).doShowSignalPath();
                }
            });
        }

        // Tap Now Playing card -> Scroll main list position to current playing song
        View cardView = view.findViewById(R.id.sheet_now_playing_card);
        if (cardView != null) {
            cardView.setOnClickListener(v -> {
                dismiss();
                if (getActivity() instanceof apincer.android.mmate.ui.MainActivity && playbackService != null) {
                    Track currentTrack = playbackService.getNowPlayingSong();
                    if (currentTrack != null) {
                        ((apincer.android.mmate.ui.MainActivity) getActivity()).scrollToSong(currentTrack);
                    }
                }
            });
        }



        // Clear queue
        view.findViewById(R.id.btn_clear_queue).setOnClickListener(v -> {
            if (isPlaybackServiceBound && playbackService != null) {
                playbackService.getQueueManager().emptyPlayingQueue();
                Toast.makeText(getContext(), "Queue cleared", Toast.LENGTH_SHORT).show();
                // Refresh the queue list
                populateSheet(view);
            }
        });

        // If service is already bound (unlikely but safe), populate immediately
        if (isPlaybackServiceBound) {
            populateSheet(view);
        }
    }

    private void updatePlaybackProgress(@Nullable View view, @Nullable PlaybackState state) {
        if (view == null || !isAdded()) return;

        Track track = playbackService != null ? playbackService.getNowPlayingSong() : null;

        // Play / Pause button state
        ImageView sheetBtnPlayPause = view.findViewById(R.id.sheet_btn_play_pause);
        if (sheetBtnPlayPause != null && state != null) {
            boolean isPlaying = state.currentState == PlaybackState.State.PLAYING;
            sheetBtnPlayPause.setImageResource(isPlaying ? R.drawable.ic_baseline_pause_24 : R.drawable.ic_baseline_play_arrow_24);
        }

        // Seekbar & position text
        if (track != null && track.getAudioDuration() > 0) {
            long durationMs = (long) (track.getAudioDuration() * 1000);
            long currentMs = state != null ? state.currentPositionSecond * 1000 : 0;

            TextView currentTimeView = view.findViewById(R.id.sheet_current_time);
            if (currentTimeView != null) {
                currentTimeView.setText(formatTime(currentMs));
            }

            SeekBar seekBar = view.findViewById(R.id.sheet_seekbar);
            if (seekBar != null && durationMs > 0) {
                seekBar.setProgress((int) ((currentMs * 1000) / durationMs));
            }
        }
    }

    // ── Populate ─────────────────────────────────────────────────────────────

    private void populateSheet(@Nullable View view) {
        if (view == null || !isAdded()) return;

        // ── Now Playing info ─────────────────────────────────────────────────
        Track track = playbackService != null ? playbackService.getNowPlayingSong() : null;

        ShapeableImageView albumArt = view.findViewById(R.id.sheet_album_art);
        TextView titleView = view.findViewById(R.id.sheet_track_title);
        TextView artistView = view.findViewById(R.id.sheet_artist);
        TextView formatBadge = view.findViewById(R.id.sheet_format_badge);
        TextView playerBadge = view.findViewById(R.id.sheet_player_badge);
        TextView techDetails = view.findViewById(R.id.sheet_tech_details);

        if (track != null) {
            titleView.setText(track.getTitle());
            artistView.setText(track.getArtist());

            // Cover art
            if (albumArt != null && getContext() != null) {
                ImageRequest request = CoverartFetcher.builder(requireContext(), track)
                        .data(track)
                        .size(240, 240)
                        .target(new coil3.target.ImageViewTarget(albumArt))
                        .build();
                SingletonImageLoader.get(requireContext()).enqueue(request);
            }

            // Format badge: "FLAC" / "DSD" / "MQA" etc.
            String encoding = track.getAudioEncoding();
            if (encoding != null && !encoding.isEmpty()) {
                formatBadge.setText(encoding.toUpperCase(Locale.US));
                formatBadge.setVisibility(View.VISIBLE);
            } else {
                formatBadge.setVisibility(View.GONE);
            }

            // Technical line: e.g. "352.8 kHz · 24bit · Lossless"
            techDetails.setText(buildTechLine(track));

        } else {
            titleView.setText("No track loaded");
            artistView.setText("");
            formatBadge.setVisibility(View.GONE);
            techDetails.setText("");
        }

        // ── Seekbar & Time indicators ──
        SeekBar seekBar = view.findViewById(R.id.sheet_seekbar);
        TextView currentTimeView = view.findViewById(R.id.sheet_current_time);
        TextView totalTimeView = view.findViewById(R.id.sheet_total_time);

        if (track != null && track.getAudioDuration() > 0) {
            long durationMs = (long) (track.getAudioDuration() * 1000);
            if (totalTimeView != null) {
                totalTimeView.setText(formatTime(durationMs));
            }

            if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                PlaybackState state = ((apincer.android.mmate.ui.MainActivity) getActivity()).getLastPlaybackState();
                updatePlaybackProgress(view, state);
            }

            if (seekBar != null) {
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                        if (fromUser && durationMs > 0) {
                            long seekMs = (progress * durationMs) / 1000;
                            if (currentTimeView != null) {
                                currentTimeView.setText(formatTime(seekMs));
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar sb) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar sb) {
                        if (isPlaybackServiceBound && playbackService != null && durationMs > 0) {
                            long seekMs = (sb.getProgress() * durationMs) / 1000;
                            playbackService.seekTo(seekMs);
                        }
                    }
                });
            }
        }

        // Player badge
        if (playbackService != null && playbackService.getPlayer() != null) {
            playerBadge.setText(PlayerNameUtils.getDropdownPlayerLabel(playbackService.getPlayer()));
            playerBadge.setVisibility(View.VISIBLE);
        } else {
            playerBadge.setVisibility(View.GONE);
        }

        // ── Transport controls in Now Playing card ───────────────────────────
        ImageView sheetBtnPrevious = view.findViewById(R.id.sheet_btn_previous);
        ImageView sheetBtnPlayPause = view.findViewById(R.id.sheet_btn_play_pause);
        ImageView sheetBtnNext = view.findViewById(R.id.sheet_btn_next);

        if (sheetBtnPrevious != null) {
            sheetBtnPrevious.setOnClickListener(v -> {
                if (isPlaybackServiceBound && playbackService != null) {
                    playbackService.skipToPrevious();
                    view.postDelayed(() -> populateSheet(view), 200);
                }
            });
        }

        if (sheetBtnPlayPause != null) {
            boolean isPlaying = false;
            if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                apincer.music.core.playback.PlaybackState state = ((apincer.android.mmate.ui.MainActivity) getActivity()).getLastPlaybackState();
                isPlaying = state != null && state.currentState == apincer.music.core.playback.PlaybackState.State.PLAYING;
            }
            final boolean currentlyPlaying = isPlaying;
            sheetBtnPlayPause.setImageResource(currentlyPlaying ? R.drawable.ic_baseline_pause_24 : R.drawable.ic_baseline_play_arrow_24);
            sheetBtnPlayPause.setOnClickListener(v -> {
                if (isPlaybackServiceBound && playbackService != null) {
                    if (currentlyPlaying) {
                        playbackService.pausePlayer();
                    } else {
                        Track current = playbackService.getNowPlayingSong();
                        if (current != null) {
                            playbackService.playSong(current);
                        }
                    }
                    view.postDelayed(() -> populateSheet(view), 200);
                }
            });
        }

        if (sheetBtnNext != null) {
            sheetBtnNext.setOnClickListener(v -> {
                if (isPlaybackServiceBound && playbackService != null) {
                    playbackService.skipToNextInQueue();
                    view.postDelayed(() -> populateSheet(view), 200);
                }
            });
        }

        // ── Queue list ────────────────────────────────────────────────────────
        QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
        if (qm != null) {
            qm.loadPlayingQueue();
        }

        // Shuffle toggle UI state & listener
        ImageView btnShuffle = view.findViewById(R.id.btn_toggle_shuffle);
        if (btnShuffle != null) {
            boolean isShuffle = qm != null && qm.isShuffle();
            androidx.core.widget.ImageViewCompat.setImageTintList(btnShuffle,
                    androidx.core.content.ContextCompat.getColorStateList(
                            requireContext(), isShuffle ? R.color.colorGold : R.color.colorMuted));
            btnShuffle.setOnClickListener(v -> {
                if (qm != null) {
                    qm.setShuffle(!isShuffle);
                    Toast.makeText(getContext(), qm.isShuffle() ? "Shuffle ON" : "Shuffle OFF", Toast.LENGTH_SHORT).show();
                    populateSheet(view);
                }
            });
        }

        // Repeat toggle UI state & listener (Cycle: OFF -> ALL -> ONE -> OFF)
        ImageView btnRepeat = view.findViewById(R.id.btn_toggle_repeat);
        if (btnRepeat != null) {
            QueueManager.RepeatMode mode = qm != null ? qm.getRepeatMode() : QueueManager.RepeatMode.OFF;
            if (mode == QueueManager.RepeatMode.ONE) {
                btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_one_24);
                androidx.core.widget.ImageViewCompat.setImageTintList(btnRepeat,
                        androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.colorGold));
            } else if (mode == QueueManager.RepeatMode.ALL) {
                btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_24);
                androidx.core.widget.ImageViewCompat.setImageTintList(btnRepeat,
                        androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.colorGold));
            } else {
                btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_24);
                androidx.core.widget.ImageViewCompat.setImageTintList(btnRepeat,
                        androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.colorMuted));
            }

            btnRepeat.setOnClickListener(v -> {
                if (qm != null) {
                    QueueManager.RepeatMode nextMode;
                    if (mode == QueueManager.RepeatMode.OFF) nextMode = QueueManager.RepeatMode.ALL;
                    else if (mode == QueueManager.RepeatMode.ALL) nextMode = QueueManager.RepeatMode.ONE;
                    else nextMode = QueueManager.RepeatMode.OFF;

                    qm.setRepeatMode(nextMode);
                    Toast.makeText(getContext(), "Repeat: " + nextMode.name(), Toast.LENGTH_SHORT).show();
                    populateSheet(view);
                }
            });
        }
        List<Track> queue = (qm != null) ? new ArrayList<>(qm.getSongs()) : new ArrayList<>();



        View btnClearQueue = view.findViewById(R.id.btn_clear_queue);
        if (btnClearQueue != null) {
            btnClearQueue.setOnClickListener(v -> {
                if (qm != null) {
                    qm.emptyPlayingQueue();
                    Toast.makeText(getContext(), "Queue cleared", Toast.LENGTH_SHORT).show();
                    populateSheet(view);
                }
            });
        }

        TextView emptyMsg = view.findViewById(R.id.sheet_empty_queue_msg);
        RecyclerView recycler = view.findViewById(R.id.sheet_queue_list);
        TextView queueLabel = view.findViewById(R.id.sheet_queue_label);

        if (queue.isEmpty()) {
            emptyMsg.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
            queueLabel.setText("Queue • empty");
        } else {
            emptyMsg.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            queueLabel.setText("Queue  •  " + queue.size() + " track" + (queue.size() != 1 ? "s" : ""));

            // Find current track index for highlighting
            String currentKey = (track != null) ? track.getUniqueKey() : null;

            QueueAdapter adapter = new QueueAdapter(queue, currentKey, selectedTrack -> {
                if (isPlaybackServiceBound && playbackService != null) {
                    playbackService.playSong(selectedTrack);
                    populateSheet(view);
                }
            });
            recycler.setLayoutManager(new LinearLayoutManager(getContext()));
            recycler.setAdapter(adapter);

            // ItemTouchHelper for Drag-and-Drop Reordering and Swipe-to-Remove
            androidx.recyclerview.widget.ItemTouchHelper touchHelper = new androidx.recyclerview.widget.ItemTouchHelper(
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                    androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                    androidx.recyclerview.widget.ItemTouchHelper.LEFT | androidx.recyclerview.widget.ItemTouchHelper.RIGHT
                ) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                        int fromPos = viewHolder.getBindingAdapterPosition();
                        int toPos = target.getBindingAdapterPosition();
                        if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION && fromPos != toPos) {
                            if (qm != null) {
                                qm.moveTrack(fromPos, toPos);
                            }
                            Track movedItem = queue.remove(fromPos);
                            queue.add(toPos, movedItem);
                            adapter.notifyItemMoved(fromPos, toPos);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        int pos = viewHolder.getBindingAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            Track removedTrack = queue.get(pos);
                            if (qm != null) {
                                qm.removeTrack(pos);
                            }
                            queue.remove(pos);
                            adapter.notifyItemRemoved(pos);
                            queueLabel.setText("Queue  •  " + queue.size() + " track" + (queue.size() != 1 ? "s" : ""));

                            if (getView() != null) {
                                com.google.android.material.snackbar.Snackbar.make(getView(), "Removed " + removedTrack.getTitle(), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                    .setAction("UNDO", v -> {
                                        if (qm != null) {
                                            qm.addPlayingQueue(removedTrack.getId());
                                        }
                                        populateSheet(getView());
                                    }).show();
                            }
                        }
                    }
                }
            );
            touchHelper.attachToRecyclerView(recycler);
        }
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        long min = sec / 60;
        sec = sec % 60;
        return String.format(Locale.US, "%02d:%02d", min, sec);
    }

    // ── Tech line helper ─────────────────────────────────────────────────────

    private String buildTechLine(Track track) {
        StringBuilder sb = new StringBuilder();

        long sampleRate = track.getAudioSampleRate();
        if (sampleRate > 0) {
            if (sampleRate % 1000 == 0) {
                sb.append(sampleRate / 1000).append(" kHz");
            } else {
                sb.append(String.format(Locale.US, "%.1f kHz", sampleRate / 1000.0));
            }
        }

        int bitDepth = track.getAudioBitsDepth();
        if (bitDepth > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(bitDepth).append("bit");
        }

        long bitRate = track.getAudioBitRate();
        if (bitRate > 0 && bitDepth == 0) { // lossy: show kbps instead
            if (sb.length() > 0) sb.append(" · ");
            sb.append(bitRate).append(" kbps");
        }

        return sb.toString();
    }

    // ── Inner adapter ─────────────────────────────────────────────────────────

    interface OnTrackClickListener {
        void onTrackClick(Track track);
    }

    private static class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.VH> {

        private final List<Track> queue;
        private final String currentKey;
        private final OnTrackClickListener listener;

        QueueAdapter(List<Track> queue, @Nullable String currentKey, OnTrackClickListener listener) {
            this.queue = queue;
            this.currentKey = currentKey;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_queue_track, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Track track = queue.get(position);
            boolean isCurrent = currentKey != null
                    && currentKey.equals(track.getUniqueKey());

            // Position indicator: ▶ for current, number otherwise
            holder.position.setText(isCurrent ? "▶" : String.valueOf(position + 1));
            holder.position.setTextColor(isCurrent
                    ? 0xFFFFD700  // gold
                    : holder.position.getResources().getColor(
                            android.R.color.darker_gray, null));

            holder.title.setText(track.getTitle());
            holder.title.setTextColor(isCurrent ? 0xFFFFD700 : 0xFFFFFFFF);

            String artist = track.getArtist();
            holder.artist.setText((artist != null && !artist.isEmpty()) ? artist : track.getAlbum());

            // Duration
            double durSec = track.getAudioDuration();
            if (durSec > 0) {
                int mins = (int) durSec / 60;
                int secs = (int) durSec % 60;
                holder.duration.setText(String.format(Locale.US, "%d:%02d", mins, secs));
            } else {
                holder.duration.setText("");
            }

            // Highlight background for current track
            holder.itemView.setBackgroundColor(
                    isCurrent ? 0x22FFD700 : android.graphics.Color.TRANSPARENT);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTrackClick(track);
                }
            });
        }

        @Override
        public int getItemCount() { return queue.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView position, title, artist, duration;
            VH(@NonNull View itemView) {
                super(itemView);
                position = itemView.findViewById(R.id.queue_item_position);
                title = itemView.findViewById(R.id.queue_item_title);
                artist = itemView.findViewById(R.id.queue_item_artist);
                duration = itemView.findViewById(R.id.queue_item_duration);
            }
        }
    }
}
