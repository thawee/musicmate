import re

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

if "private apincer.android.mmate.ui.compose.NowPlayingState nowPlayingState" not in content:
    content = content.replace(
        "private apincer.android.mmate.ui.compose.QueueState queueState",
        "private apincer.android.mmate.ui.compose.NowPlayingState nowPlayingState = new apincer.android.mmate.ui.compose.NowPlayingState();\n    private apincer.android.mmate.ui.compose.QueueState queueState"
    )

inflate_target = "viewNowPlayingPage = LayoutInflater.from(getContext()).inflate(R.layout.sheet_now_playing_queue, null);"
inflate_replacement = """
        viewNowPlayingPage = DialogInterop.createNowPlayingPageView(
            requireContext(),
            nowPlayingState,
            () -> {
                if (playbackService != null) {
                    if (playbackService.isPlaying()) playbackService.pause();
                    else playbackService.play();
                }
            },
            () -> { if (playbackService != null) playbackService.skipToNextInQueue(); },
            () -> { if (playbackService != null) playbackService.skipToPrevious(); },
            () -> {
                if (playbackService != null) {
                    boolean shuffle = !playbackService.isShuffleModeEnabled();
                    playbackService.setShuffleMode(shuffle ? PlaybackService.SHUFFLE_MODE_ALL : PlaybackService.SHUFFLE_MODE_NONE);
                    nowPlayingState.getIsShuffle().setValue(shuffle);
                }
            },
            () -> {
                if (playbackService != null) {
                    int mode = playbackService.getRepeatMode();
                    int nextMode = (mode == PlaybackService.REPEAT_MODE_NONE) ? PlaybackService.REPEAT_MODE_ALL :
                                   (mode == PlaybackService.REPEAT_MODE_ALL) ? PlaybackService.REPEAT_MODE_ONE : PlaybackService.REPEAT_MODE_NONE;
                    playbackService.setRepeatMode(nextMode);
                    nowPlayingState.getRepeatMode().setValue(nextMode);
                }
            },
            (progress) -> {
                if (playbackService != null) {
                    long duration = nowPlayingState.getDurationMs().getValue();
                    if (duration > 0) playbackService.seekTo((long) (progress * duration));
                }
            },
            () -> {}, // Vol Down
            () -> {}, // Vol Up
            (vol) -> {}, // Vol Change
            () -> {
                if (playbackService != null && playbackService.getNowPlayingSong() != null) {
                    dismiss();
                    if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
                        ((apincer.android.mmate.ui.MainActivity) getActivity()).scrollToSong(playbackService.getNowPlayingSong());
                    }
                }
            }
        );
"""
content = content.replace(inflate_target, inflate_replacement)

# Replace populateNowPlayingSheet
pop_start = content.find("private void populateNowPlayingSheet(@Nullable View view) {")
if pop_start != -1:
    brace_count = 0
    pop_end = -1
    for i in range(pop_start, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                pop_end = i + 1
                break
                
    new_pop = """private void populateNowPlayingSheet(@Nullable View view) {
        if (view == null || !isAdded()) return;

        Track track = playbackService != null ? playbackService.getNowPlayingSong() : null;
        nowPlayingState.getTrack().setValue(track);

        if (track != null) {
            nowPlayingState.getSpecsFormat().setValue(TagUtils.formatCodec(track) + " • " + formatShortResolution(track));
            long bitrate = track.getAudioBitRate();
            nowPlayingState.getSpecsBitrate().setValue(bitrate > 0 ? bitrate + " kbps" : "Lossless Audio");
            double dr = track.getDrScore() > 0 ? track.getDrScore() : track.getDynamicRange();
            nowPlayingState.getSpecsDr().setValue(dr > 0 ? "Dynamic Range: DR " + (int)dr : "Studio Master Dynamic");
            nowPlayingState.getSpecsFileSize().setValue(track.getFileSize() > 0 && getContext() != null ? android.text.format.Formatter.formatFileSize(getContext(), track.getFileSize()) : "Hi-Res Audio");
            
            long durationMs = (long) (track.getAudioDuration() * 1000);
            nowPlayingState.getDurationMs().setValue(durationMs);
            
            if (getContext() != null) {
                coil3.request.ImageRequest request = CoverartFetcher.builder(requireContext(), track)
                        .data(track)
                        .size(240, 240)
                        .target(new coil3.target.Target() {
                            @Override
                            public void onSuccess(android.graphics.drawable.Drawable result) {
                                if (result instanceof android.graphics.drawable.BitmapDrawable) {
                                    nowPlayingState.getAlbumArt().setValue(((android.graphics.drawable.BitmapDrawable) result).getBitmap());
                                }
                            }
                            @Override
                            public void onError(android.graphics.drawable.Drawable error) {
                                nowPlayingState.getAlbumArt().setValue(null);
                            }
                        })
                        .build();
                coil3.SingletonImageLoader.get(requireContext()).enqueue(request);
            }
        } else {
            nowPlayingState.getAlbumArt().setValue(null);
        }

        if (playbackService != null) {
            nowPlayingState.getIsShuffle().setValue(playbackService.isShuffleModeEnabled());
            nowPlayingState.getRepeatMode().setValue(playbackService.getRepeatMode());
        }

        if (getActivity() instanceof apincer.android.mmate.ui.MainActivity) {
            PlaybackState state = ((apincer.android.mmate.ui.MainActivity) getActivity()).getLastPlaybackState();
            if (state != null) {
                nowPlayingState.getPlaybackState().setValue(state);
                nowPlayingState.getProgressMs().setValue(state.getPosition());
            }
        }
    }"""
    content = content[:pop_start] + new_pop + content[pop_end:]

# Wipe out setupNowPlayingTab and setupArtworkGestures and toggleTechSpecsFlip because they are all handled by Compose!
def wipe_method(method_decl):
    global content
    idx = content.find(method_decl)
    if idx != -1:
        brace_count = 0
        end_idx = -1
        for i in range(idx, len(content)):
            if content[i] == '{':
                brace_count += 1
            elif content[i] == '}':
                brace_count -= 1
                if brace_count == 0:
                    end_idx = i + 1
                    break
        content = content[:idx] + method_decl.split("(")[0] + "() { /* Migrated to Compose */ }" + content[end_idx:]

wipe_method("private void setupNowPlayingTab(View view) {")
wipe_method("private void setupArtworkGestures(View view) {")
wipe_method("private void toggleTechSpecsFlip(@Nullable View view) {")

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
