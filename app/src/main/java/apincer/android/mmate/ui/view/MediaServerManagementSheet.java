package apincer.android.mmate.ui.view;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import apincer.android.mmate.R;
import apincer.android.mmate.service.MediaServerManager;
import apincer.music.core.server.spi.MediaServerHub;

public class MediaServerManagementSheet extends BottomSheetDialogFragment {
    public static final String TAG = "MediaServerManagementSheet";

    private TextView tvServerStatus;
    private TextView tvServerAddress;
    private TextView tvServerPowerBy;
    private ImageView qrCodeImage;
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

        tvServerStatus = view.findViewById(R.id.server_status);
        tvServerAddress = view.findViewById(R.id.server_address);
        tvServerPowerBy = view.findViewById(R.id.server_power_by);
        qrCodeImage = view.findViewById(R.id.qr_code_image);

        btnStartServer = view.findViewById(R.id.btn_start_server);
        btnStopServer = view.findViewById(R.id.btn_stop_server);

        serverManager = new MediaServerManager(requireContext());

        btnStartServer.setOnClickListener(v -> serverManager.startServer());
        btnStopServer.setOnClickListener(v -> serverManager.stopServer());

        observeServerStatus();
        detectWebEngine();
        return view;
    }

    private void detectWebEngine() {
        String webEngine = serverManager.getWebEngineName();
        if (tvServerPowerBy != null) {
            tvServerPowerBy.setText(webEngine);
        }
    }

    private void observeServerStatus() {
        serverManager.getServerStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null) { // Default to stopped if null initially
                status = MediaServerHub.ServerStatus.STOPPED;
            }
            tvServerStatus.setText(status.name()); // Use the name of the enum constant

            switch (status) {
                case RUNNING:
                    // case INITIALIZED: // If you use this intermediate state
                    btnStartServer.setEnabled(false);
                    btnStopServer.setEnabled(true);
                    tvServerAddress.setVisibility(VISIBLE); // Handled by address observer
                    tvServerAddress.setText(serverManager.getServerLocation());
                    qrCodeImage.setVisibility(VISIBLE);
                    generateAndSetQRCode(serverManager.getServerLocation());
                    break;
                case STOPPED:
                case ERROR:
                    btnStartServer.setEnabled(true);
                    btnStopServer.setEnabled(false);
                    qrCodeImage.setVisibility(GONE);
                    tvServerAddress.setVisibility(GONE);
                    break;
                case STARTING:
                    // case STOPPING: // You might want a STOPPING state from service too
                    qrCodeImage.setVisibility(GONE);
                    tvServerAddress.setVisibility(GONE);
                    btnStartServer.setEnabled(false);
                    btnStopServer.setEnabled(false);
                    break;
            }
        });
    }

    /**
     * Generates a QR code from the given text and sets it to the ImageView.
     * @param text The text to encode in the QR code.
     */
    private void generateAndSetQRCode(String text) {
        // Initialize the QR code writer
        QRCodeWriter writer = new QRCodeWriter();
        try {
            // Encode the text into a BitMatrix
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512);

            // Create a Bitmap from the BitMatrix
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            // Populate the Bitmap with the BitMatrix data
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            // Set the generated Bitmap to the ImageView
            qrCodeImage.setImageBitmap(bitmap);

        } catch (WriterException ignore) {
            // Log the exception and show a toast message
           // e.printStackTrace();
        }
    }
}
