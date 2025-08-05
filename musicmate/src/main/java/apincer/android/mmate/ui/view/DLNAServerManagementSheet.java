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
import apincer.android.mmate.dlna.DLNAServerManager;

public class DLNAServerManagementSheet extends BottomSheetDialogFragment {

    public static final String TAG = "DLNAServerManagementSheet";

    private TextView tvServerStatus;
    private TextView tvServerAddress;
    private Button btnStartServer;
    private Button btnStopServer;

    private DLNAServerManager dlnaServerManager; // Get instance

    public static DLNAServerManagementSheet newInstance() {
        return new DLNAServerManagementSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_dln_server_management, container, false);

        tvServerStatus = view.findViewById(R.id.tv_server_status);
        tvServerAddress = view.findViewById(R.id.tv_server_address);
        btnStartServer = view.findViewById(R.id.btn_start_server);
        btnStopServer = view.findViewById(R.id.btn_stop_server);

        // It's better if the Activity/Fragment that shows this sheet provides the DLNAServerManager
        // or a ViewModel that holds it. For simplicity here:
        dlnaServerManager = DLNAServerManager.getInstance(requireContext());

        btnStartServer.setOnClickListener(v -> dlnaServerManager.startServer());
        btnStopServer.setOnClickListener(v -> dlnaServerManager.stopServer());

        observeServerStatus();

        return view;
    }

    private void observeServerStatus() {
        dlnaServerManager.getServerStatus().observe(getViewLifecycleOwner(), status -> {
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

        dlnaServerManager.getServerAddress().observe(getViewLifecycleOwner(), address -> {
            tvServerAddress.setText("Location: "+address);
        });
    }
}
