package apincer.android.mmate.ui.view;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

import apincer.android.mmate.R;
import apincer.android.mmate.service.PlaybackServiceImpl;
import apincer.music.core.playback.ExternalPlayer;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.StringUtils;
import apincer.android.mmate.utils.AudioOutputHelper;

public class SignalPathBottomSheet extends BottomSheetDialogFragment {
    private boolean isPlayersExpanded = true;
    private LinearLayout dlnaPlayersContainer;
    private ImageView expandArrow;

    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound = false;

    @Override
    public void onStart() {
        super.onStart();
        // Bind to the service in onStart, which is a good place to handle resources that should be active when the fragment is visible
        Intent intent = new Intent(getContext(), PlaybackServiceImpl.class);
        getContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        // Unbind from the service in onStop to prevent memory leaks
        if (isPlaybackServiceBound) {
            getContext().unbindService(serviceConnection);
            isPlaybackServiceBound = false;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        return inflater.inflate(R.layout.layout_signal_path_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get the LinearLayout where the DLNA players will be added
        dlnaPlayersContainer = view.findViewById(R.id.dlna_players_container);
        expandArrow = view.findViewById(R.id.expand_arrow);
        TextView qualityIndicator = view.findViewById(R.id.quality_indicator);
        qualityIndicator.setText("");

        // Set up click listener for the expandable section
        LinearLayout playersHeader = view.findViewById(R.id.players_header);
        playersHeader.setOnClickListener(v -> togglePlayersVisibility());
    }

    private void populateDMCAPlayers() {
        List<PlaybackTarget> renderers = playbackService.getAvaiablePlaybackTargets();
        dlnaPlayersContainer.removeAllViews();
        if (renderers != null && !renderers.isEmpty()) {
            for (PlaybackTarget player : renderers) {
                TextView playerView = new TextView(getContext());
                playerView.setText(player.getDisplayName());
                playerView.setTextColor(Color.BLUE);
                playerView.setPadding(32, 32, 32, 32);
                playerView.setBackgroundResource(R.drawable.rounded_bg);
                playerView.setOnClickListener(v -> {
                    if(isPlaybackServiceBound) {
                        playbackService.switchPlayer(player);
                        Toast.makeText(getContext(), "Selected: " + player.getDisplayName(), Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                });
                dlnaPlayersContainer.addView(playerView);
            }
        }
    }

    // Helper function to add a step to the signal path
    private void togglePlayersVisibility() {
        if (isPlayersExpanded) {
            dlnaPlayersContainer.setVisibility(View.GONE);
            expandArrow.animate().rotation(0).setDuration(200).start();
        } else {
            dlnaPlayersContainer.setVisibility(View.VISIBLE);
            expandArrow.animate().rotation(180).setDuration(200).start();
        }
        isPlayersExpanded = !isPlayersExpanded;
    }

    // Helper function to add a step to the signal path
    private void addSignalPathStep(LinearLayout container, String title, String description, boolean hasNext) {
        View stepView = LayoutInflater.from(getContext()).inflate(R.layout.signal_path_step, container, false);
        TextView titleTextView = stepView.findViewById(R.id.step_title);
        TextView descriptionTextView = stepView.findViewById(R.id.step_description);
        View lineView = stepView.findViewById(R.id.vertical_line);

        titleTextView.setText(title);
        descriptionTextView.setText(description);

        if (hasNext) {
            lineView.setVisibility(View.VISIBLE);
        } else {
            lineView.setVisibility(View.INVISIBLE);
        }

        container.addView(stepView);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackServiceImpl.PlaybackServiceBinder binder = (PlaybackServiceImpl.PlaybackServiceBinder) service;
            playbackService = binder.getService();
            isPlaybackServiceBound = true;

            addSignalPathSteps();
            populateDMCAPlayers();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isPlaybackServiceBound = false;
        }
    };

    private void addSignalPathSteps() {
        // This method will now be called AFTER the service is connected
        LinearLayout signalPathContainer = requireView().findViewById(R.id.signal_path_container);
        signalPathContainer.removeAllViews(); // Clear previous views if any

        TextView qualityIndicator = requireView().findViewById(R.id.quality_indicator);
        qualityIndicator.setText("");

        // Step 1: Song
        MediaTrack song = playbackService.getNowPlayingSong();
        if (song != null) {
            // Add the "Lossless" indicator at the very top
            String quality = song.getAudioEncoding().toUpperCase()+", "+ StringUtils.formatAudioSampleRate(song.getAudioSampleRate(), true) + ", "+ StringUtils.formatAudioBitsDepth(song.getAudioBitsDepth());
            TextView resolutionIndicator = new TextView(getContext());
            resolutionIndicator.setText(quality.trim());
            resolutionIndicator.setTextColor(ResourcesCompat.getColor(getResources(), android.R.color.holo_green_light, getContext().getTheme()));
            resolutionIndicator.setTextSize(14f);
            resolutionIndicator.setPadding(0, 0, 0, 8);
            signalPathContainer.addView(resolutionIndicator);

            qualityIndicator.setText(song.getQualityInd());

            addSignalPathStep(signalPathContainer, "Source", song.getSimpleName(), true);
        }

        PlaybackTarget playbackTarget = playbackService.getPlayer();
        if (playbackTarget != null) {
            String playerDetails = playbackTarget.getDisplayName();
            String serverDetails = "MusicMate MediaServer ["+ ApplicationUtils .getDeviceModel()+"]\n" + playbackService.getServerLocation();
            if (playbackTarget.isStreaming()) {
                playerDetails = playerDetails + "\n" + playbackTarget.getDescription();
                addSignalPathStep(signalPathContainer, "Media Server", serverDetails, true);
                addSignalPathStep(signalPathContainer, "Renderer", playerDetails, false);
            }else if (playbackTarget instanceof ExternalPlayer player) {
                playerDetails = playerDetails +"\n"+player.getDescription();
                addSignalPathStep(signalPathContainer, "Player", playerDetails, false);
            }else {
                addSignalPathStep(signalPathContainer, "Player", playerDetails, false);
                AudioOutputHelper.getOutputDevice(getContext(), device -> {
                    String deviceDetails = device.getName()+"\n"+device.getCodec()+", "+device.getBitPerSampling()+", "+device.getSamplingRate();
                    addSignalPathStep(signalPathContainer, "Device", deviceDetails, false);
                });
            }
        }

        // --- Populate DLNA players ---
        populateDMCAPlayers();

        // Set the initial state of the collapsible list based on the active player
        isPlayersExpanded = playbackTarget != null;
        togglePlayersVisibility();
    }
}