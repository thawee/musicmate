package apincer.android.mmate.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static apincer.android.mmate.service.MusicMateServiceImpl.SERVER_STATUS_NO_WIFI;
import static apincer.android.mmate.service.MusicMateServiceImpl.SERVER_STATUS_OFFLINE;
import static apincer.android.mmate.service.MusicMateServiceImpl.SERVER_STATUS_ONLINE_PREFIX;
import static apincer.music.core.utils.StringUtils.SYMBOL_ENC_SEP;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import apincer.android.mmate.R;
import apincer.android.mmate.coil3.CoverartFetcher;
import apincer.android.mmate.service.MusicMateServiceImpl;
import apincer.android.mmate.ui.viewmodel.MediaServerViewModel;
import apincer.android.mmate.utils.AudioOutputHelper;
import apincer.android.mmate.utils.TagUIUtils;
import apincer.music.core.Constants;
import apincer.music.core.model.Track;
import apincer.music.core.playback.DMRPlayer;
import apincer.music.core.playback.ExternalAndroidPlayer;
import apincer.music.core.playback.PlaybackState;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.repository.QueueManager;
import apincer.music.core.server.spi.MediaServerHub;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.NetworkUtils;
import apincer.music.core.utils.PlayerNameUtils;
import apincer.music.core.utils.StringUtils;
import apincer.music.core.utils.TagUtils;
import coil3.SingletonImageLoader;
import coil3.request.ImageRequest;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Master Unified Audio Hub Bottom Sheet combining:
 * 1. Top ViewPager2 swappable playback area (Now Playing Card | Signal Path | Media Server)
 * 2. Permanent bottom Queue section visible across all pages.
 */
@AndroidEntryPoint
public class AudioHubBottomSheet extends BottomSheetDialogFragment {
    public static final String TAG = "AudioHubBottomSheet";
    private static final String ARG_INITIAL_TAB = "ARG_INITIAL_TAB";

    public static final int TAB_NOW_PLAYING = 0;
    public static final int TAB_QUEUE = 1;
    public static final int TAB_MEDIA_SERVER = 2;

    private int initialTab = TAB_NOW_PLAYING;

    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound = false;
    private MediaServerViewModel mediaServerViewModel;

    // ViewPager & Segmented Tab UI
    private ViewPager2 viewPager;
    private MaterialButtonToggleGroup tabToggleGroup;

    // Master Header Actions
    private ImageView btnCastHeader;

    // Pre-inflated Pages
    private View viewNowPlayingPage;
    private View viewQueuePage;
    private View viewMediaServerPage;

    // Media Server UI
    private TextView tvServerName;
    private TextView tvServerStatus;
    private View tvServerStatusIcon;
    private TextView tvServerAddress;
    private TextView tvServerPowerBy;
    private ImageView qrCodeImage;
    private Button btnStartServer;
    private Button btnStopServer;

    public AudioHubBottomSheet() {
        // Default constructor
    }

    public static AudioHubBottomSheet newInstance() {
        return newInstance(TAB_NOW_PLAYING);
    }

    public static AudioHubBottomSheet newInstance(int initialTab) {
        AudioHubBottomSheet sheet = new AudioHubBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_INITIAL_TAB, initialTab);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            initialTab = getArguments().getInt(ARG_INITIAL_TAB, TAB_NOW_PLAYING);
        }
        mediaServerViewModel = new ViewModelProvider(this).get(MediaServerViewModel.class);
    }

    private AutoCloseable songSubscription;
    private AutoCloseable stateSubscription;

    private void subscribeToServiceUpdates() {
        unsubscribeFromServiceUpdates();
        if (playbackService == null) return;

        songSubscription = playbackService.subscribeNowPlayingSong(
                optTrack -> {
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(this::refreshUI);
                    }
                },
                err -> Log.e(TAG, "Error observing song change", err)
        );

        stateSubscription = playbackService.subscribePlaybackState(
                state -> {
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (viewNowPlayingPage != null) {
                                updatePlaybackProgress(viewNowPlayingPage, state);
                            }
                        });
                    }
                },
                err -> Log.e(TAG, "Error observing playback state", err)
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
        if (getContext() != null) {
            Intent intent = new Intent(getContext(), MusicMateServiceImpl.class);
            getContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }

        if (getActivity() instanceof apincer.android.mmate.ui.MainActivity ma && ma.isPlaybackServiceBound()) {
            playbackService = ma.getPlaybackService();
            isPlaybackServiceBound = true;
            subscribeToServiceUpdates();
            refreshUI();
        }

        if (getDialog() != null) {
            View container = getDialog().findViewById(com.google.android.material.R.id.container);
            View coordinator = getDialog().findViewById(com.google.android.material.R.id.coordinator);
            View touchOutside = getDialog().findViewById(com.google.android.material.R.id.touch_outside);
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {
                bottomSheet.setBackground(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);

                float density = getResources().getDisplayMetrics().density;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int bottomNavMargin = (int) (120 * density);

                if (getActivity() instanceof apincer.android.mmate.ui.MainActivity ma) {
                    View navBar = ma.findViewById(R.id.bottom_navigation_container);
                    if (navBar != null) {
                        int[] loc = new int[2];
                        navBar.getLocationOnScreen(loc);
                        int navBarTopOnScreen = loc[1];
                        if (navBarTopOnScreen > 0 && navBarTopOnScreen < screenHeight) {
                            bottomNavMargin = (screenHeight - navBarTopOnScreen) + (int) (8 * density);
                        } else if (navBar.getHeight() > 0) {
                            bottomNavMargin = navBar.getHeight() + (int) (48 * density);
                        }
                    }
                }

                if (coordinator != null) {
                    coordinator.setPadding(0, 0, 0, bottomNavMargin);
                }

                int targetHeight = (int) (screenHeight * 0.65);
                int maxHeight = screenHeight - bottomNavMargin - (int) (64 * density);
                if (targetHeight > maxHeight) {
                    targetHeight = maxHeight;
                }
                int minHeight = (int) (420 * density);
                if (targetHeight < minHeight) {
                    targetHeight = minHeight;
                }

                ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
                lp.height = targetHeight;
                bottomSheet.setLayoutParams(lp);

                behavior.setPeekHeight(targetHeight);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
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
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_audio_hub, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabToggleGroup = view.findViewById(R.id.audio_hub_tab_group);
        viewPager = view.findViewById(R.id.audio_hub_view_pager);

        btnCastHeader = view.findViewById(R.id.btn_select_target_player);

        View closeBtn = view.findViewById(R.id.btn_close_audio_hub);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dismiss());
        }

        if (btnCastHeader != null) {
            btnCastHeader.setOnClickListener(v -> showPlayerPicker(btnCastHeader));
        }

        if (tabToggleGroup != null) {
            tabToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    if (checkedId == R.id.tab_now_playing) {
                        viewPager.setCurrentItem(TAB_NOW_PLAYING, true);
                    } else if (checkedId == R.id.tab_queue) {
                        viewPager.setCurrentItem(TAB_QUEUE, true);
                    } else if (checkedId == R.id.tab_media_server) {
                        viewPager.setCurrentItem(TAB_MEDIA_SERVER, true);
                    }
                }
            });
        }

        // Disable nested scrolling on ViewPager2 so BottomSheetBehavior ignores it
        // and correctly targets the Queue RecyclerView for vertical nested scrolling.
        if (viewPager != null && viewPager.getChildCount() > 0) {
            View child = viewPager.getChildAt(0);
            if (child instanceof RecyclerView) {
                child.setNestedScrollingEnabled(false);
            }
        }

        // Pre-inflate page views for ViewPager2
        viewNowPlayingPage = LayoutInflater.from(getContext()).inflate(R.layout.sheet_now_playing_queue, null);
        viewQueuePage = LayoutInflater.from(getContext()).inflate(R.layout.view_audio_hub_queue_page, null);
        viewMediaServerPage = LayoutInflater.from(getContext()).inflate(R.layout.view_action_server_management_bottom_sheet, null);

        flattenPage(viewNowPlayingPage, true);
        flattenPage(viewQueuePage, false);
        flattenPage(viewMediaServerPage, false);

        setupNowPlayingTab(viewNowPlayingPage);
        setupMediaServerTab(viewMediaServerPage);

        // ViewPager2 Adapter
        viewPager.setAdapter(new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                FrameLayout container = new FrameLayout(parent.getContext());
                container.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                return new RecyclerView.ViewHolder(container) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                FrameLayout container = (FrameLayout) holder.itemView;
                container.removeAllViews();
                View pageView;
                if (position == TAB_MEDIA_SERVER) {
                    pageView = viewMediaServerPage;
                } else if (position == TAB_QUEUE) {
                    pageView = viewQueuePage;
                } else {
                    pageView = viewNowPlayingPage;
                }
                if (pageView.getParent() != null) {
                    ((ViewGroup) pageView.getParent()).removeView(pageView);
                }
                pageView.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                container.addView(pageView);
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePageIndicator(position);
            }
        });

        viewPager.setCurrentItem(initialTab, false);
        updatePageIndicator(initialTab);
    }

    /**
     * Strips redundant outer backgrounds, elevations, margins, duplicate drag handles,
     * duplicate inner headers, and duplicate inner queue sections so pages blend seamlessly.
     */
    private void flattenPage(@Nullable View pageView, boolean isNowPlaying) {
        if (pageView == null) return;
        pageView.setBackground(null);
        pageView.setElevation(0f);
        pageView.setPadding(0, 0, 0, 0);

        if (pageView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params) {
            params.setMargins(0, 0, 0, 0);
        }

        if (pageView instanceof ViewGroup vg) {
            if (isNowPlaying) {
                // For Now Playing page: hide drag handle, inner header, and inner queue section
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child.getId() != R.id.sheet_now_playing_card) {
                        child.setVisibility(GONE);
                    }
                }
            } else if (pageView == viewMediaServerPage) {
                // For Server page: hide duplicate inner header row
                if (vg.getChildCount() > 0) {
                    View firstChild = vg.getChildAt(0);
                    if (firstChild != null) firstChild.setVisibility(GONE);
                }
            }
        }
    }

    private void updatePageIndicator(int position) {
        if (getContext() == null) return;

        if (tabToggleGroup != null) {
            int targetId;
            if (position == TAB_MEDIA_SERVER) {
                targetId = R.id.tab_media_server;
            } else if (position == TAB_QUEUE) {
                targetId = R.id.tab_queue;
            } else {
                targetId = R.id.tab_now_playing;
            }
            if (tabToggleGroup.getCheckedButtonId() != targetId) {
                tabToggleGroup.check(targetId);
            }
        }

        if (position == TAB_NOW_PLAYING && isPlaybackServiceBound && viewNowPlayingPage != null) {
            populateNowPlayingSheet(viewNowPlayingPage);
        } else if (position == TAB_QUEUE && isPlaybackServiceBound && viewQueuePage != null) {
            populateQueueSection(viewQueuePage);
        } else if (position == TAB_MEDIA_SERVER && viewMediaServerPage != null) {
            if (mediaServerViewModel != null && mediaServerViewModel.getServerStatus() != null) {
                updateServerUI(mediaServerViewModel.getServerStatus().getValue());
            }
        }
    }

    // ── Service Connection ───────────────────────────────────────────────────

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicMateServiceImpl.MusicMateServiceImplBinder binder =
                    (MusicMateServiceImpl.MusicMateServiceImplBinder) service;
            playbackService = binder.getService();
            isPlaybackServiceBound = true;

            subscribeToServiceUpdates();
            refreshUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            unsubscribeFromServiceUpdates();
            isPlaybackServiceBound = false;
            playbackService = null;
        }
    };

    // ── Page 0: Now Playing Card ─────────────────────────────────────────────

    private void setupNowPlayingTab(View view) {
        View cardView = view.findViewById(R.id.sheet_now_playing_card);
        if (cardView != null) {
            cardView.setOnClickListener(v -> {
                if (playbackService != null && playbackService.getNowPlayingSong() != null) {
                    dismiss();
                    if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                        Track currentTrack = playbackService.getNowPlayingSong();
                        if (currentTrack != null) {
                            ((apincer.android.mmate.ui.MainActivity) getActivity()).scrollToSong(currentTrack);
                        }
                    }
                }
            });
        }

       // setupVolumeControl(view);
        setupArtworkGestures(view);
    }

    private void animateGestureFeedback(View albumArt, float translationX) {
        if (albumArt == null) return;
        albumArt.animate().cancel();
        albumArt.setTranslationX(translationX);
        albumArt.setAlpha(0.6f);
        albumArt.animate()
                .translationX(0f)
                .alpha(1.0f)
                .setDuration(220)
                .start();
    }

    private void setupArtworkGestures(View view) {
        if (view == null || getContext() == null) return;
        View albumArt = view.findViewById(R.id.sheet_album_art);
        if (albumArt == null) return;

        GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null && e2 != null) {
                    float diffX = e2.getX() - e1.getX();
                    float diffY = e2.getY() - e1.getY();
                    if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 80 && Math.abs(velocityX) > 150) {
                        if (diffX < 0) {
                            if (isPlaybackServiceBound && playbackService != null) {
                                animateGestureFeedback(albumArt, -35f);
                                playbackService.skipToNextInQueue();
                                populateNowPlayingSheet(viewNowPlayingPage);
                            }
                        } else {
                            if (isPlaybackServiceBound && playbackService != null) {
                                animateGestureFeedback(albumArt, 35f);
                                playbackService.skipToPrevious();
                                populateNowPlayingSheet(viewNowPlayingPage);
                            }
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (isPlaybackServiceBound && playbackService != null) {
                    albumArt.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100)
                            .withEndAction(() -> albumArt.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start())
                            .start();
                    boolean isPlaying = false;
                    if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                        PlaybackState state =
                                ((apincer.android.mmate.ui.MainActivity) getActivity()).getLastPlaybackState();
                        isPlaying = state != null && state.currentState == PlaybackState.State.PLAYING;
                    }
                    if (isPlaying) {
                        playbackService.pausePlayer();
                    } else {
                        Track current = playbackService.getNowPlayingSong();
                        if (current != null) {
                            playbackService.playSong(current);
                        }
                    }
                    populateNowPlayingSheet(viewNowPlayingPage);
                    return true;
                }
                return false;
            }
        });

        albumArt.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            v.performClick();
            return true;
        });
    }

    private void applyAmbientGlow(@Nullable View cardView, @Nullable Drawable drawable) {
        if (cardView == null || !(drawable instanceof BitmapDrawable) || !isAdded()) return;
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        if (bitmap == null || bitmap.isRecycled()) return;

        Palette.from(bitmap).generate(palette -> {
            if (palette == null || !isAdded() || getContext() == null) return;
            int defaultColor = 0xFF1E1E2C;
            int dominantColor = palette.getVibrantColor(palette.getDominantColor(defaultColor));

            float[] hsv = new float[3];
            Color.colorToHSV(dominantColor, hsv);
            hsv[1] = Math.min(hsv[1], 0.55f);
            hsv[2] = Math.max(0.18f, Math.min(hsv[2], 0.32f));
            int glowColor = Color.HSVToColor(hsv);

            float radius = 16 * getResources().getDisplayMetrics().density;
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{ glowColor, 0xFF12121A }
            );
            bg.setCornerRadius(radius);
            cardView.setBackground(bg);
        });
    }

    private void populateNowPlayingSheet(@Nullable View view) {
        if (view == null || !isAdded()) return;

        Track track = playbackService != null ? playbackService.getNowPlayingSong() : null;

        ShapeableImageView albumArt = view.findViewById(R.id.sheet_album_art);
        TextView titleView = view.findViewById(R.id.sheet_track_title);
        TextView artistView = view.findViewById(R.id.sheet_artist);

        if (track != null) {
            titleView.setText(track.getTitle());
            artistView.setText(track.getArtist());

            if (albumArt != null && getContext() != null) {
                ImageRequest request = CoverartFetcher.builder(requireContext(), track)
                        .data(track)
                        .size(240, 240)
                        .target(new coil3.target.ImageViewTarget(albumArt))
                        .build();
                SingletonImageLoader.get(requireContext()).enqueue(request);

                albumArt.postDelayed(() -> {
                    View card = view.findViewById(R.id.sheet_now_playing_card);
                    if (card != null && albumArt.getDrawable() != null) {
                        applyAmbientGlow(card, albumArt.getDrawable());
                    }
                }, 150);
            }

            populateSignalPathWidget(view, track);
        } else {
            titleView.setText("No track playing");
            artistView.setText("Select a song or target player to begin");
            populateSignalPathWidget(view, null);
            if (albumArt != null) albumArt.setImageResource(R.drawable.ic_now_playing_idle);
        }

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
        } else {
            if (currentTimeView != null) currentTimeView.setText("00:00");
            if (totalTimeView != null) totalTimeView.setText("00:00");
            if (seekBar != null) seekBar.setProgress(0);
        }

        PlaybackTarget target = playbackService != null ? playbackService.getPlayer() : null;
        if (btnCastHeader != null && getContext() != null) {
            boolean isRemote = target != null && target.isStreaming();
            int tintColor = isRemote
                    ? ContextCompat.getColor(requireContext(), R.color.colorGold)
                    : ContextCompat.getColor(requireContext(), R.color.colorOnSurface);
            btnCastHeader.setImageTintList(android.content.res.ColorStateList.valueOf(tintColor));
        }

        ImageView sheetBtnPrevious = view.findViewById(R.id.sheet_btn_previous);
        ImageView sheetBtnPlayPause = view.findViewById(R.id.sheet_btn_play_pause);
        ImageView sheetBtnNext = view.findViewById(R.id.sheet_btn_next);

        if (sheetBtnPrevious != null) {
            sheetBtnPrevious.setOnClickListener(v -> {
                if (isPlaybackServiceBound && playbackService != null) {
                    playbackService.skipToPrevious();
                    view.postDelayed(() -> populateNowPlayingSheet(view), 200);
                }
            });
        }

        if (sheetBtnPlayPause != null) {
            boolean isPlaying = false;
            if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                PlaybackState state =
                        ((apincer.android.mmate.ui.MainActivity) getActivity()).getLastPlaybackState();
                isPlaying = state != null && state.currentState == PlaybackState.State.PLAYING;
            }
            final boolean currentlyPlaying = isPlaying;
            sheetBtnPlayPause.setImageResource(currentlyPlaying ? R.drawable.ic_pause_rounded : R.drawable.ic_play_rounded);
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
                    view.postDelayed(() -> populateNowPlayingSheet(view), 200);
                }
            });
        }

        if (sheetBtnNext != null) {
            sheetBtnNext.setOnClickListener(v -> {
                if (isPlaybackServiceBound && playbackService != null) {
                    playbackService.skipToNextInQueue();
                    view.postDelayed(() -> populateNowPlayingSheet(view), 200);
                }
            });
        }

        // Shuffle toggle inside Now Playing transport row
        ImageView btnShuffle = view.findViewById(R.id.btn_toggle_shuffle);
        if (btnShuffle != null && getContext() != null) {
            QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
            boolean isShuffle = qm != null && qm.isShuffle();
            androidx.core.widget.ImageViewCompat.setImageTintList(btnShuffle,
                    ContextCompat.getColorStateList(requireContext(), isShuffle ? R.color.colorGold : R.color.colorMuted));
            btnShuffle.setOnClickListener(v -> {
                if (qm != null) {
                    qm.setShuffle(!isShuffle);
                    Toast.makeText(getContext(), qm.isShuffle() ? "Shuffle ON" : "Shuffle OFF", Toast.LENGTH_SHORT).show();
                    populateNowPlayingSheet(view);
                }
            });
        }

        // Repeat toggle inside Now Playing transport row
        ImageView btnRepeat = view.findViewById(R.id.btn_toggle_repeat);
        if (btnRepeat != null && getContext() != null) {
            QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
            QueueManager.RepeatMode mode = qm != null ? qm.getRepeatMode() : QueueManager.RepeatMode.OFF;
            if (mode == QueueManager.RepeatMode.ONE) {
                btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_one_24);
                androidx.core.widget.ImageViewCompat.setImageTintList(btnRepeat,
                        ContextCompat.getColorStateList(requireContext(), R.color.colorGold));
            } else if (mode == QueueManager.RepeatMode.ALL) {
                btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_24);
                androidx.core.widget.ImageViewCompat.setImageTintList(btnRepeat,
                        ContextCompat.getColorStateList(requireContext(), R.color.colorGold));
            } else {
                btnRepeat.setImageResource(R.drawable.ic_baseline_repeat_24);
                androidx.core.widget.ImageViewCompat.setImageTintList(btnRepeat,
                        ContextCompat.getColorStateList(requireContext(), R.color.colorMuted));
            }

            btnRepeat.setOnClickListener(v -> {
                if (qm != null) {
                    QueueManager.RepeatMode nextMode;
                    if (mode == QueueManager.RepeatMode.OFF) nextMode = QueueManager.RepeatMode.ALL;
                    else if (mode == QueueManager.RepeatMode.ALL) nextMode = QueueManager.RepeatMode.ONE;
                    else nextMode = QueueManager.RepeatMode.OFF;

                    qm.setRepeatMode(nextMode);
                    Toast.makeText(getContext(), "Repeat: " + nextMode.name(), Toast.LENGTH_SHORT).show();
                    populateNowPlayingSheet(view);
                }
            });
        }

        // Also update queue list when now playing updates
        if (getView() != null) {
            populateQueueSection(getView());
        }
    }

    public void refreshUI() {
        if (!isAdded()) return;
        if (viewNowPlayingPage != null) {
            populateNowPlayingSheet(viewNowPlayingPage);
        }
        if (viewQueuePage != null) {
            populateQueueSection(viewQueuePage);
        }
        if (viewMediaServerPage != null && mediaServerViewModel != null && mediaServerViewModel.getServerStatus() != null) {
            updateServerUI(mediaServerViewModel.getServerStatus().getValue());
        }
    }

    // ── Permanent Bottom Queue Section ────────────────────────────────────────

    private void populateQueueSection(@NonNull View root) {
        QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
        if (qm != null) {
            qm.loadPlayingQueue();
        }

        List<Track> queue = (qm != null) ? new ArrayList<>(qm.getSongs()) : new ArrayList<>();

        View btnClearQueue = root.findViewById(R.id.btn_clear_queue);
        if (btnClearQueue != null) {
            btnClearQueue.setOnClickListener(v -> {
                if (qm != null) {
                    qm.emptyPlayingQueue();
                    Toast.makeText(getContext(), "Queue cleared", Toast.LENGTH_SHORT).show();
                    populateQueueSection(root);
                }
            });
        }

        TextView emptyMsg = root.findViewById(R.id.sheet_empty_queue_msg);
        RecyclerView recycler = root.findViewById(R.id.sheet_queue_list);
        TextView queueLabel = root.findViewById(R.id.sheet_queue_label);
        Track track = playbackService != null ? playbackService.getNowPlayingSong() : null;

        if (recycler != null) {
            updateQueueHeader(root, queue);
            if (queue.isEmpty()) {
                if (emptyMsg != null) emptyMsg.setVisibility(VISIBLE);
                recycler.setVisibility(GONE);
            } else {
                if (emptyMsg != null) emptyMsg.setVisibility(GONE);
                recycler.setVisibility(VISIBLE);

                String currentKey = (track != null) ? track.getUniqueKey() : null;
                int playingPosition = -1;
                if (currentKey != null) {
                    for (int i = 0; i < queue.size(); i++) {
                        if (currentKey.equals(queue.get(i).getUniqueKey())) {
                            playingPosition = i;
                            break;
                        }
                    }
                }

                QueueAdapter existingAdapter = null;
                if (recycler.getAdapter() instanceof QueueAdapter) {
                    existingAdapter = (QueueAdapter) recycler.getAdapter();
                }

                if (existingAdapter != null) {
                    existingAdapter.updateData(queue, currentKey);
                } else {
                    QueueAdapter adapter = new QueueAdapter(queue, currentKey, selectedTrack -> {
                        if (isPlaybackServiceBound && playbackService != null) {
                            playbackService.playSong(selectedTrack);
                            if (viewNowPlayingPage != null) populateNowPlayingSheet(viewNowPlayingPage);
                            populateQueueSection(root);
                        }
                    });
                    LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
                    recycler.setLayoutManager(layoutManager);
                    recycler.setNestedScrollingEnabled(true);
                    recycler.setAdapter(adapter);

                    ItemTouchHelper touchHelper = new ItemTouchHelper(
                            new ItemTouchHelper.SimpleCallback(
                                    ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                                    ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
                            ) {
                                @Override
                                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                                    int fromPos = viewHolder.getBindingAdapterPosition();
                                    int toPos = target.getBindingAdapterPosition();
                                    if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION && fromPos != toPos) {
                                        if (qm != null) {
                                            qm.moveTrack(fromPos, toPos);
                                        }
                                        QueueAdapter currentAdapter = (QueueAdapter) recyclerView.getAdapter();
                                        if (currentAdapter != null) {
                                            Track movedItem = currentAdapter.queue.remove(fromPos);
                                            currentAdapter.queue.add(toPos, movedItem);
                                            currentAdapter.notifyItemMoved(fromPos, toPos);
                                            int start = Math.min(fromPos, toPos);
                                            int count = Math.abs(fromPos - toPos) + 1;
                                            currentAdapter.notifyItemRangeChanged(start, count);
                                        }
                                        return true;
                                    }
                                    return false;
                                }

                                @Override
                                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                                    int pos = viewHolder.getBindingAdapterPosition();
                                    QueueAdapter currentAdapter = (QueueAdapter) recycler.getAdapter();
                                    if (currentAdapter != null && pos != RecyclerView.NO_POSITION && pos < currentAdapter.queue.size()) {
                                        Track removedTrack = currentAdapter.queue.get(pos);
                                        if (qm != null) {
                                            qm.removeTrack(pos);
                                        }
                                        currentAdapter.queue.remove(pos);
                                        currentAdapter.notifyItemRemoved(pos);
                                        
                                        // Update headers only
                                        if (qm != null) {
                                            updateQueueHeader(root, qm.getSongs());
                                        }

                                        if (getView() != null) {
                                            Snackbar.make(getView(), "Removed " + removedTrack.getTitle(), Snackbar.LENGTH_LONG)
                                                    .setAction("UNDO", v -> {
                                                        if (qm != null) {
                                                            qm.addPlayingQueue(removedTrack.getId());
                                                        }
                                                        populateQueueSection(root);
                                                    }).show();
                                        }
                                    }
                                }
                            }
                    );
                    touchHelper.attachToRecyclerView(recycler);
                }

                if (playingPosition >= 0) {
                    final int scrollPos = playingPosition;
                    recycler.post(() -> {
                        if (recycler.getLayoutManager() instanceof LinearLayoutManager lm) {
                            lm.scrollToPositionWithOffset(scrollPos, 0);
                        }
                    });
                }
            }
        }
    }

    private void updateQueueHeader(@NonNull View root, List<Track> queue) {
        TextView queueLabel = root.findViewById(R.id.sheet_queue_label);
        TextView queueSubtitle = root.findViewById(R.id.sheet_queue_subtitle);
        RecyclerView recycler = root.findViewById(R.id.sheet_queue_list);
        TextView emptyMsg = root.findViewById(R.id.sheet_empty_queue_msg);

        if (queue == null || queue.isEmpty()) {
            if (queueLabel != null) queueLabel.setText("Upcoming Queue");
            if (queueSubtitle != null) queueSubtitle.setText("0 tracks");
            if (recycler != null) recycler.setVisibility(View.GONE);
            if (emptyMsg != null) emptyMsg.setVisibility(View.VISIBLE);
            return;
        } else {
            if (recycler != null) recycler.setVisibility(View.VISIBLE);
            if (emptyMsg != null) emptyMsg.setVisibility(View.GONE);
        }

        double totalDurSec = 0;
        for (Track t : queue) {
            if (t != null) totalDurSec += t.getAudioDuration();
        }
        String durStr = "";
        if (totalDurSec > 0) {
            int totalMins = (int) (totalDurSec / 60);
            if (totalMins >= 60) {
                int hrs = totalMins / 60;
                int mins = totalMins % 60;
                durStr = String.format(Locale.US, "%dh %dmin total", hrs, mins);
            } else {
                durStr = String.format(Locale.US, "%d min total", totalMins);
            }
        }

        String countStr = queue.size() + " track" + (queue.size() != 1 ? "s" : "");
        if (queueLabel != null) queueLabel.setText("Upcoming Queue");
        if (queueSubtitle != null) {
            queueSubtitle.setText(!durStr.isEmpty() ? countStr + " • " + durStr : countStr);
        } else if (queueLabel != null) {
            queueLabel.setText("Queue  •  " + countStr + (!durStr.isEmpty() ? " (" + durStr + ")" : ""));
        }
    }

    private void updatePlaybackProgress(@Nullable View view, @Nullable PlaybackState state) {
        if (view == null || !isAdded() || state == null) return;

        Track track = playbackService != null ? playbackService.getNowPlayingSong() : null;

        ImageView sheetBtnPlayPause = view.findViewById(R.id.sheet_btn_play_pause);
        if (sheetBtnPlayPause != null) {
            boolean isPlaying = state.currentState == PlaybackState.State.PLAYING;
            sheetBtnPlayPause.setImageResource(isPlaying ? R.drawable.ic_baseline_pause_24 : R.drawable.ic_baseline_play_arrow_24);
        }

        if (track != null && track.getAudioDuration() > 0) {
            long durationMs = (long) (track.getAudioDuration() * 1000);
            long currentMs = state.currentPositionSecond * 1000L;

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

    private void showPlayerPicker(View anchorView) {
        if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
            ((apincer.android.mmate.ui.MainActivity) getActivity()).showPlayerPickerPopup(anchorView);
        }
    }

    // ── Page 1: Audio Signal Path ────────────────────────────────────────────

    private String formatShortResolution(Track track) {
        if (track == null) return "";
        int bitDepth = track.getAudioBitsDepth();
        long sampleRate = track.getAudioSampleRate();
        long bitRate = track.getAudioBitRate();

        if (sampleRate > 0) {
            String rateStr = (sampleRate % 1000 == 0)
                    ? (sampleRate / 1000) + "k"
                    : String.format(Locale.US, "%.1fk", sampleRate / 1000.0);
            if (bitDepth > 0) {
                return bitDepth + "/" + rateStr;
            }
            return rateStr;
        } else if (bitRate > 0) {
            return (bitRate / 1000) + "k";
        }
        return "";
    }

    private void populateSignalPathWidget(@NonNull View view, @Nullable Track track) {
        View widgetView = view.findViewById(R.id.sheet_signal_path_widget);
        TextView verdictView = view.findViewById(R.id.sheet_signal_verdict);

        TextView sourceTitle = view.findViewById(R.id.sheet_node_source_title);
        TextView engineSubtitle = view.findViewById(R.id.sheet_node_engine_subtitle);

        View targetBox = view.findViewById(R.id.sheet_node_target_box);
        TextView targetTitle = view.findViewById(R.id.sheet_node_target_title);

        View expandableContainer = view.findViewById(R.id.sheet_signal_path_expandable);
        LinearLayout stepsContainer = view.findViewById(R.id.sheet_signal_path_steps_container);

        if (track != null && getContext() != null) {
            String quality = TagUIUtils.getQualityIndFullString(track);
            if (verdictView != null) {
                verdictView.setText(VerdictFormatter.format(getContext(), quality));
            }

            if (sourceTitle != null) {
                String codec = TagUtils.formatCodec(track);
                if (codec == null || codec.isEmpty()) codec = track.getAudioEncoding().toUpperCase();
                String res = formatShortResolution(track);
                if (!res.isEmpty()) {
                    sourceTitle.setText(codec + " " + res);
                } else {
                    sourceTitle.setText(codec);
                }
            }

            PlaybackTarget target = playbackService != null ? playbackService.getPlayer() : null;
            boolean isStreaming = target != null && target.isStreaming();

            if (engineSubtitle != null) {
                engineSubtitle.setText(isStreaming ? "MusicMate Server" : "Local");
            }

            if (targetTitle != null) {
                String playerLabel = target != null ? target.getDisplayName() : "Local Device";
                boolean isBitPerfect = false;

                if (target == null || target instanceof ExternalAndroidPlayer || (target != null && !target.isStreaming())) {
                    AudioOutputHelper.Device device = AudioOutputHelper.getOutputDevice(getContext(), track);
                    String devName = device.getName();
                    isBitPerfect = device.isBitPerfect();
                    if (devName != null && !devName.isEmpty() && !devName.equals("Phone Speaker")) {
                        playerLabel = devName;
                    } else if (target != null) {
                        playerLabel = target.getDisplayName();
                    } else if (devName != null && !devName.isEmpty()) {
                        playerLabel = devName;
                    } else {
                        playerLabel = "Speaker";
                    }
                }

                targetTitle.setText(playerLabel + " ▾");

                if (targetBox != null) {
                    targetBox.setBackgroundResource(isBitPerfect ? R.drawable.shape_node_target_bitperfect : R.drawable.shape_node_target);
                }
            }

            if (targetBox != null) {
                targetBox.setOnClickListener(v -> showPlayerPicker(targetBox));
            }

            if (widgetView != null) {
                widgetView.setOnClickListener(v -> {
                    if (expandableContainer != null) {
                        boolean isCurrentlyVisible = expandableContainer.getVisibility() == VISIBLE;
                        ViewGroup sceneRoot = (view.getParent() instanceof ViewGroup) ? (ViewGroup) view.getParent() : null;
                        if (sceneRoot != null) {
                            android.transition.TransitionManager.beginDelayedTransition(sceneRoot, new android.transition.AutoTransition().setDuration(200));
                        }
                        if (!isCurrentlyVisible) {
                            expandableContainer.setVisibility(VISIBLE);
                           // if (chevron != null) chevron.animate().rotation(180f).setDuration(200).start();
                            if (stepsContainer != null) {
                                stepsContainer.removeAllViews();
                                addSignalPathSteps(stepsContainer);
                            }
                        } else {
                            expandableContainer.setVisibility(GONE);
                           // if (chevron != null) chevron.animate().rotation(0f).setDuration(200).start();
                        }
                    }
                });
            }
        } else {
            if (verdictView != null) verdictView.setText("No active track");
            if (sourceTitle != null) sourceTitle.setText("-");
            if (engineSubtitle != null) engineSubtitle.setText("-");
            if (targetTitle != null) targetTitle.setText("Select Player ▾");
            if (targetBox != null) {
                targetBox.setOnClickListener(v -> showPlayerPicker(targetBox));
            }
        }
    }

    private void addSignalPathSteps(LinearLayout signalPathContainer) {
        if (signalPathContainer == null || playbackService == null || getContext() == null) return;
        signalPathContainer.removeAllViews();

        Track song = playbackService.getNowPlayingSong();
        if (song == null) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("No active audio route telemetry available.");
            emptyText.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorMuted));
            emptyText.setTextSize(12f);
            signalPathContainer.addView(emptyText);
            return;
        }

        boolean isLossyFile = TagUtils.isLossy(song);
        boolean isHiRes = TagUtils.isHiRes(song);

        // --- Step 1: Source ---
        int sourceShape = R.drawable.shape_node_source;
        int sourceIcon = R.drawable.ic_baseline_audio_file_24;
        String sourceTitle = isLossyFile ? "Source: Lossy Compressed Audio" : (isHiRes ? "Source: Hi-Res Lossless Audio" : "Source: Lossless CD Quality");
        String songTitleText = song.getTitle();
        if (!StringUtils.isEmpty(song.getArtist())) {
            songTitleText = songTitleText + " — " + song.getArtist();
        }

        String codec = TagUtils.formatCodec(song);
        if (codec == null || codec.isEmpty()) {
            codec = song.getAudioEncoding().toUpperCase();
        }

        String sourceBadge = isHiRes ? "HI-RES" : (isLossyFile ? "LOSSY" : "LOSSLESS");
        String sourceText = songTitleText + "\n" +
                "Format: " + codec + SYMBOL_ENC_SEP + TagUtils.formatResolution(song.getAudioBitsDepth(), song.getAudioSampleRate(), song.getMqaSampleRate()) +
                " (" + (song.getAudioBitRate() > 0 ? (song.getAudioBitRate() / 1000) + " kbps" : "VBR") + ")\n" +
                "Container: " + (song.getFileType() != null ? song.getFileType().toUpperCase() : "AUDIO");

        addSignalPathStep(signalPathContainer, sourceShape, sourceIcon, sourceTitle, sourceBadge, sourceText, true);

        // --- Step 2: Transport Engine ---
        PlaybackTarget playbackTarget = playbackService.getPlayer();
        if (playbackTarget != null) {
            if (playbackTarget.isStreaming() && !(playbackTarget instanceof DMRPlayer) && playbackTarget.getDescription() != null) {
                String incomingIp = NetworkUtils.extractIpAddress(playbackTarget.getDescription());
                List<PlaybackTarget> targets = playbackService.getPlaybackTargets();
                if (targets != null && !incomingIp.isEmpty()) {
                    for (PlaybackTarget target : targets) {
                        if (target instanceof DMRPlayer dmr && dmr.getDescription() != null) {
                            String devIp = NetworkUtils.extractIpAddress(dmr.getDescription());
                            if (incomingIp.equals(devIp)) {
                                playbackTarget = dmr;
                                break;
                            }
                        }
                    }
                }
            }

            boolean isStreaming = playbackTarget.isStreaming();
            int transportShape = R.drawable.shape_node_transport;
            int transportIcon = R.drawable.ic_baseline_audio_path_24;
            String transportTitle = "MusicMate Transport Engine";
            String transportBadge = isStreaming ? "NETWORK STREAMER" : "LOCAL TRANSPORT";
            String serverDetails = ApplicationUtils.getFriendlyDeviceName() + " • v" + ApplicationUtils.getVersionNumber(getContext()) + "\n" +
                    (isStreaming ? "Streaming via HTTP Media Server Pipeline" : "Direct Local Storage IO Read");

            addSignalPathStep(signalPathContainer, transportShape, transportIcon, transportTitle, transportBadge, serverDetails, true);

            // --- Step 3: Target Audio Output ---
            int targetShape;
            int targetIcon;
            String targetTitle;
            String targetBadge;
            String targetDetails;

            if (playbackTarget instanceof ExternalAndroidPlayer player) {
                AudioOutputHelper.Device device = AudioOutputHelper.getOutputDevice(getContext(), song);
                boolean isBitPerfect = device.isBitPerfect();
                targetShape = isBitPerfect ? R.drawable.shape_node_target_bitperfect : R.drawable.shape_node_target;
                targetIcon = (device.getResId() != 0) ? device.getResId() : (isBitPerfect ? R.drawable.ic_baseline_usb_24 : R.drawable.ic_baseline_volume_up_24);
                targetTitle = "Output Device: " + device.getName();
                targetBadge = isBitPerfect ? "BIT-PERFECT" : (device.getDescription() != null && device.getDescription().startsWith("BT") ? "BLUETOOTH A2DP" : "DIRECT SYSTEM OUTPUT");

                StringBuilder devBuf = new StringBuilder();
                devBuf.append("Type: ").append(device.getDescription());
                if (!StringUtils.isEmpty(device.getCodec())) {
                    devBuf.append(" • ").append(device.getCodec());
                }
                devBuf.append("\nSpecs: ").append(TagUtils.formatResolution(device.getBitPerSampling(), device.getSamplingRate(), -1));
                if (isBitPerfect) {
                    devBuf.append("\nStatus: Bit-Perfect Direct USB Hardware Passthrough (1:1)");
                } else {
                    devBuf.append("\nStatus: ").append(device.getFriendyDescription());
                }
                targetDetails = devBuf.toString();
            } else {
                targetShape = R.drawable.shape_node_target;
                targetIcon = R.drawable.rounded_broadcast_on_personal_24;
                targetTitle = "Network Target: " + playbackTarget.getDisplayName();
                targetBadge = "DLNA RENDERER";
                String playerLabel = PlayerNameUtils.getTwoLinePlayerLabel(playbackTarget);
                targetDetails = playerLabel + "\nProtocol: UPnP AVTransport / DLNA Render";
            }

            addSignalPathStep(signalPathContainer, targetShape, targetIcon, targetTitle, targetBadge, targetDetails, false);
        }
    }

    private void addSignalPathStep(LinearLayout container, int shapeResId, int iconResId, String title, String badgeText, String description, boolean hasNext) {
        if (getContext() == null) return;
        View stepView = LayoutInflater.from(getContext()).inflate(R.layout.signal_path_step, container, false);
        View cardContainer = stepView.findViewById(R.id.step_card_container);
        ImageView iconView = stepView.findViewById(R.id.step_icon);
        MaterialTextView titleTextView = stepView.findViewById(R.id.step_title);
        TextView badgeTextView = stepView.findViewById(R.id.step_badge);
        MaterialTextView descriptionTextView = stepView.findViewById(R.id.step_description);
        View lineView = stepView.findViewById(R.id.vertical_line);

        if (cardContainer != null) {
            cardContainer.setBackgroundResource(shapeResId);
        }
        if (iconView != null) iconView.setImageResource(iconResId);
        if (titleTextView != null) titleTextView.setText(title);

        if (badgeTextView != null) {
            if (StringUtils.isEmpty(badgeText)) {
                badgeTextView.setVisibility(GONE);
            } else {
                badgeTextView.setVisibility(VISIBLE);
                badgeTextView.setText(badgeText);
            }
        }

        if (descriptionTextView != null) descriptionTextView.setText(description);

        if (lineView != null) {
            lineView.setVisibility(hasNext ? VISIBLE : View.INVISIBLE);
        }

        container.addView(stepView);
    }

    // ── Page 2: Media Server Management ──────────────────────────────────────

    private void setupMediaServerTab(View view) {
        View serverHeader = view.findViewById(R.id.server_name);
        if (serverHeader != null && serverHeader.getParent() instanceof View parentHeader) {
            parentHeader.setVisibility(GONE);
        }

        tvServerName = view.findViewById(R.id.server_name);
        tvServerStatus = view.findViewById(R.id.server_status);
        tvServerStatusIcon = view.findViewById(R.id.status_indicator);

        tvServerAddress = view.findViewById(R.id.server_address);
        tvServerPowerBy = view.findViewById(R.id.server_power_by);
        qrCodeImage = view.findViewById(R.id.qr_code_image);

        btnStartServer = view.findViewById(R.id.btn_start_server);
        btnStopServer = view.findViewById(R.id.btn_stop_server);

        if (btnStartServer != null) {
            btnStartServer.setOnClickListener(v -> mediaServerViewModel.startServer());
        }
        if (btnStopServer != null) {
            btnStopServer.setOnClickListener(v -> mediaServerViewModel.stopServer());
        }

        if (tvServerName != null) {
            tvServerName.setText(Constants.getPresentationName());
        }

        observeServerStatus();
        detectWebEngine();
    }

    private void detectWebEngine() {
        String webEngine = mediaServerViewModel.getLibraryName();
        if (tvServerPowerBy != null) {
            tvServerPowerBy.setText(webEngine);
        }
    }

    private void observeServerStatus() {
        mediaServerViewModel.getServerStatus().observe(getViewLifecycleOwner(), this::updateServerUI);
    }

    private void updateServerUI(MediaServerHub.ServerStatus status) {
        if (viewMediaServerPage == null || getContext() == null) return;
        boolean isNetworkAvailable = NetworkUtils.isWifiConnected(requireContext()) || NetworkUtils.isHotspotActive(requireContext());

        switch (status) {
            case RUNNING:
                if (btnStartServer != null) btnStartServer.setVisibility(GONE);
                if (btnStopServer != null) {
                    btnStopServer.setVisibility(VISIBLE);
                    btnStopServer.setEnabled(true);
                }
                if (tvServerAddress != null) tvServerAddress.setVisibility(VISIBLE);
                if (qrCodeImage != null) qrCodeImage.setVisibility(VISIBLE);

                String ssid = ApplicationUtils.getWifiSSID(getContext());
                if (!StringUtils.isEmpty(ssid)) {
                    if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_ONLINE_PREFIX + " (" + ssid + ")");
                } else if (NetworkUtils.isHotspotActive(requireContext())) {
                    if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_ONLINE_PREFIX + " (Hotspot)");
                } else if (NetworkUtils.isWifiConnected(requireContext())) {
                    if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_ONLINE_PREFIX);
                } else {
                    if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_NO_WIFI);
                }
                if (tvServerStatusIcon != null) tvServerStatusIcon.setBackgroundResource(R.drawable.shape_circle_green);

                String serverLocation = mediaServerViewModel.getServerLocationUrl();
                if (tvServerAddress != null) tvServerAddress.setText(serverLocation);
                detectWebEngine();
                generateAndSetQRCode(serverLocation);
                break;

            case STOPPED:
            case ERROR:
                if (btnStartServer != null) {
                    btnStartServer.setVisibility(VISIBLE);
                    btnStartServer.setEnabled(isNetworkAvailable);
                }
                if (btnStopServer != null) btnStopServer.setVisibility(GONE);
                if (qrCodeImage != null) qrCodeImage.setVisibility(GONE);

                if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_OFFLINE);
                if (tvServerStatusIcon != null) tvServerStatusIcon.setBackgroundResource(R.drawable.shape_circle_red);

                if (tvServerAddress != null) {
                    tvServerAddress.setVisibility(VISIBLE);
                    tvServerAddress.setText(isNetworkAvailable ? R.string.server_url_not_available : R.string.notification_server_not_running);
                }
                break;

            case STARTING:
                if (qrCodeImage != null) qrCodeImage.setVisibility(GONE);
                if (tvServerAddress != null) tvServerAddress.setVisibility(GONE);
                if (btnStartServer != null) btnStartServer.setEnabled(false);
                if (btnStopServer != null) btnStopServer.setEnabled(false);
                if (tvServerStatus != null) tvServerStatus.setText(SERVER_STATUS_OFFLINE);
                break;
        }
    }

    private void generateAndSetQRCode(String text) {
        if (text == null || text.isEmpty()) {
            if (qrCodeImage != null) qrCodeImage.setVisibility(GONE);
            return;
        }

        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            if (qrCodeImage != null) {
                qrCodeImage.setImageBitmap(bmp);
            }
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String formatTime(long ms) {
        long sec = ms / 1000;
        long min = sec / 60;
        sec = sec % 60;
        return String.format(Locale.US, "%02d:%02d", min, sec);
    }

    // ── Queue Adapter ─────────────────────────────────────────────────────────

    interface OnTrackClickListener {
        void onTrackClick(Track track);
    }

    private static class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.VH> {

        private final List<Track> queue;
        private String currentKey;
        private final OnTrackClickListener listener;

        QueueAdapter(List<Track> queue, @Nullable String currentKey, OnTrackClickListener listener) {
            this.queue = new ArrayList<>(queue);
            this.currentKey = currentKey;
            this.listener = listener;
        }

        public void updateData(List<Track> newQueue, @Nullable String newKey) {
            this.queue.clear();
            this.queue.addAll(newQueue);
            this.currentKey = newKey;
            notifyDataSetChanged();
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

            holder.position.setText(isCurrent ? "▶" : String.valueOf(position + 1));
            holder.position.setTextColor(isCurrent
                    ? 0xFFFFD700
                    : holder.position.getResources().getColor(
                            android.R.color.darker_gray, null));

            holder.title.setText(track.getTitle());
            holder.title.setTextColor(isCurrent ? 0xFFFFD700 : 0xFFFFFFFF);

            String artist = track.getArtist();
            holder.artist.setText((artist != null && !artist.isEmpty()) ? artist : track.getAlbum());

            double durSec = track.getAudioDuration();
            if (durSec > 0) {
                int mins = (int) durSec / 60;
                int secs = (int) durSec % 60;
                holder.duration.setText(String.format(Locale.US, "%d:%02d", mins, secs));
            } else {
                holder.duration.setText("");
            }

            holder.itemView.setBackgroundColor(
                    isCurrent ? 0x22FFD700 : Color.TRANSPARENT);

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
            final ImageView dragHandle;
            VH(@NonNull View itemView) {
                super(itemView);
                position = itemView.findViewById(R.id.queue_item_position);
                title = itemView.findViewById(R.id.queue_item_title);
                artist = itemView.findViewById(R.id.queue_item_artist);
                duration = itemView.findViewById(R.id.queue_item_duration);
                dragHandle = itemView.findViewById(R.id.queue_item_drag_handle);
            }
        }
    }
}
