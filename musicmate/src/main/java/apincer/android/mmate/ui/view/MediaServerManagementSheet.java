package apincer.android.mmate.ui.view;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
// If using ViewModel in sheet

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import apincer.android.mmate.R;
import apincer.android.mmate.dlna.MediaServerService;
import apincer.android.mmate.dlna.MediaServerManager;

public class MediaServerManagementSheet extends BottomSheetDialogFragment {
    public static final String TAG = "MediaServerManagementSheet";

    private TextView tvServerStatus;
    private TextView tvServerAddress;
    private Button btnStartServer;
    private Button btnStopServer;

    private MediaServerManager serverManager; // Get instance

    public static MediaServerManagementSheet newInstance() {
        return new MediaServerManagementSheet();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Unbind from the service in onStop to prevent memory leaks
        if (serverManager != null) {
            serverManager.cleanup();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_dln_server_management, container, false);

        tvServerStatus = view.findViewById(R.id.tv_server_status);
        tvServerAddress = view.findViewById(R.id.tv_server_address);
        btnStartServer = view.findViewById(R.id.btn_start_server);
        btnStopServer = view.findViewById(R.id.btn_stop_server);

        serverManager = new MediaServerManager(requireContext());

        btnStartServer.setOnClickListener(v -> serverManager.startServer());
        btnStopServer.setOnClickListener(v -> serverManager.stopServer());

        observeServerStatus();

        return view;
    }

    private void observeServerStatus() {
        serverManager.getServerStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null) { // Default to stopped if null initially
                status = MediaServerService.ServerStatus.STOPPED;
            }
            tvServerStatus.setText(status.name()); // Use the name of the enum constant

            switch (status) {
                case RUNNING:
                    // case INITIALIZED: // If you use this intermediate state
                    btnStartServer.setEnabled(false);
                    btnStopServer.setEnabled(true);
                    tvServerAddress.setVisibility(View.VISIBLE); // Handled by address observer
                    break;
                case STOPPED:
                case ERROR:
                    btnStartServer.setEnabled(true);
                    btnStopServer.setEnabled(false);
                    tvServerAddress.setVisibility(View.GONE);
                   // tvServerAddress.setText("Location: N/A");
                    break;
                case STARTING:
                    // case STOPPING: // You might want a STOPPING state from service too
                    btnStartServer.setEnabled(false);
                    btnStopServer.setEnabled(false);
                    break;
            }
        });

        serverManager.getServerAddress().observe(getViewLifecycleOwner(), address -> {
            tvServerAddress.setText("Location: http://"+address+"/");
        });
    }
}
