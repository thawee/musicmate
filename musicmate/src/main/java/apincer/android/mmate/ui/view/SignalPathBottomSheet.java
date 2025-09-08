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

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;

import java.util.List;

import apincer.android.mmate.R;
import apincer.android.mmate.playback.DlnaPlayer;
import apincer.android.mmate.playback.ExternalPlayer;
import apincer.android.mmate.playback.PlaybackService;
import apincer.android.mmate.playback.Player;
import apincer.android.mmate.playback.StreamingPlayer;
import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;

public class SignalPathBottomSheet extends BottomSheetDialogFragment {
    private boolean isPlayersExpanded = true;
    private LinearLayout dlnaPlayersContainer;
    private ImageView expandArrow;

    private PlaybackService playbackService;
    private boolean isBound = false;

    @Override
    public void onStart() {
        super.onStart();
        // Bind to the service in onStart, which is a good place to handle resources that should be active when the fragment is visible
        Intent intent = new Intent(getContext(), PlaybackService.class);
        getContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        // Unbind from the service in onStop to prevent memory leaks
        if (isBound) {
            getContext().unbindService(serviceConnection);
            isBound = false;
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

    private void populateDlnaPlayers() {
        List<RemoteDevice> renderers = playbackService.getRenderers();
        dlnaPlayersContainer.removeAllViews();
        if (renderers != null && !renderers.isEmpty()) {
            for (RemoteDevice device : renderers) {
                TextView playerView = new TextView(getContext());
                playerView.setText(device.getDetails().getFriendlyName());
                playerView.setTextColor(Color.BLUE);
                playerView.setPadding(32, 32, 32, 32);
                playerView.setBackgroundResource(R.drawable.rounded_bg);
                playerView.setOnClickListener(v -> {
                    if(isBound) {
                        Player player = Player.Factory.create(getContext(), device);
                        playbackService.setActivePlayer(player);
                        Toast.makeText(getContext(), "Selected: " + device.getDetails().getFriendlyName(), Toast.LENGTH_SHORT).show();
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
            PlaybackService.PlaybackServiceBinder binder = (PlaybackService.PlaybackServiceBinder) service;
            playbackService = binder.getService();
            isBound = true;

            addSignalPathSteps();
            populateDlnaPlayers();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private void addSignalPathSteps() {
        // This method will now be called AFTER the service is connected
        LinearLayout signalPathContainer = requireView().findViewById(R.id.signal_path_container);
        signalPathContainer.removeAllViews(); // Clear previous views if any

        TextView qualityIndicator = requireView().findViewById(R.id.quality_indicator);
        qualityIndicator.setText("");

        // Step 1: Song
        MusicTag song = playbackService.getNowPlaying();
        if (song != null) {
            // Add the "Lossless" indicator at the very top
            String quality = song.getAudioEncoding().toUpperCase()+", "+ StringUtils.formatAudioSampleRate(song.getAudioSampleRate(), true) + ", "+ StringUtils.formatAudioBitsDepth(song.getAudioBitsDepth());
            TextView resolutionIndicator = new TextView(getContext());
            resolutionIndicator.setText(quality.trim());
            resolutionIndicator.setTextColor(getContext().getResources().getColor(android.R.color.holo_green_light));
            resolutionIndicator.setTextSize(14f);
            resolutionIndicator.setPadding(0, 0, 0, 8);
            signalPathContainer.addView(resolutionIndicator);

            qualityIndicator.setText(MusicTagUtils.getQualityIndicator(song));

            addSignalPathStep(signalPathContainer, "Source", song.getTitle() + " - " + song.getArtist(), true);
        }

        Player playerInfo = playbackService.getActivePlayer();
        if (playerInfo != null) {
            if(isBound) {
                LocalDevice device = playbackService.getServerDevice();
                if(device != null) {
                    String serverDetails = device.getDetails().getFriendlyName();

                    if (playerInfo instanceof DlnaPlayer player) {
                        // serverDetails = serverDetails + "\n" + device.getDetails().getModelDetails().getModelDescription();
                        serverDetails = serverDetails + "\n" + device.getDetails().getPresentationURI().getHost();

                    } else if (playerInfo instanceof StreamingPlayer) {
                        serverDetails = serverDetails + "\n" + device.getDetails().getPresentationURI().getHost();
                    }

                    // Step 2: Media Server
                    if (!(playerInfo instanceof ExternalPlayer)) {
                        addSignalPathStep(signalPathContainer, "Media Server", serverDetails, true);
                    }
                }
            }

            String playerDetails = playerInfo.getDisplayName();
            if (playerInfo instanceof DlnaPlayer player) {
                // playerDetails = playerDetails +"\n"+player.getDetails();
                playerDetails = playerDetails +"\n"+player.getLocation();
            } else if (playerInfo instanceof StreamingPlayer) {
                //  playerDetails = playerDetails +"\n"+playerInfo.getDetails();
                playerDetails = playerDetails +"\n"+playerInfo.getId();
            }

            // Step 3: Player
            addSignalPathStep(signalPathContainer, "Player", playerDetails, false);
        }

        // --- Populate DLNA players ---
        populateDlnaPlayers();

        // Set the initial state of the collapsible list based on the active player
        isPlayersExpanded = playerInfo != null;
        togglePlayersVisibility();
    }
}