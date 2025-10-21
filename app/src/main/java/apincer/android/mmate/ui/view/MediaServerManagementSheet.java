package apincer.android.mmate.ui.view;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static apincer.android.mmate.service.MusicMateServiceImpl.OFFLINE_STATUS;
import static apincer.android.mmate.service.MusicMateServiceImpl.ONLINE_STATUS;

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
import apincer.android.mmate.ui.viewmodel.MediaServerViewModel;
import apincer.music.core.Constants;
import apincer.music.core.server.spi.MediaServerHub;
import apincer.music.core.utils.NetworkUtils;

import androidx.lifecycle.ViewModelProvider;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MediaServerManagementSheet extends BottomSheetDialogFragment {
    public static final String TAG = "MediaServerManagementSheet";

    private TextView tvServerName;
    private TextView tvServerStatus;
    private TextView tvServerAddress;
    private TextView tvServerPowerBy;
    private ImageView qrCodeImage;
    private Button btnStartServer;
    private Button btnStopServer;

    private MediaServerViewModel viewModel;

    public static MediaServerManagementSheet newInstance() {
        return new MediaServerManagementSheet();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // --- Initialize the ViewModel here ---
        viewModel = new ViewModelProvider(this).get(MediaServerViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_dln_server_management, container, false);
        tvServerName = view.findViewById(R.id.server_name);
        tvServerStatus = view.findViewById(R.id.server_status);
        tvServerAddress = view.findViewById(R.id.server_address);
        tvServerPowerBy = view.findViewById(R.id.server_power_by);
        qrCodeImage = view.findViewById(R.id.qr_code_image);

        btnStartServer = view.findViewById(R.id.btn_start_server);
        btnStopServer = view.findViewById(R.id.btn_stop_server);

        btnStartServer.setOnClickListener(v -> viewModel.startServer());
        btnStopServer.setOnClickListener(v -> viewModel.stopServer());

        tvServerName.setText(Constants.getPresentationName());
        observeServerStatus();
        return view;
    }

    private void detectWebEngine() {
        String webEngine = viewModel.getLibraryName();
        if (tvServerPowerBy != null) {
            tvServerPowerBy.setText(webEngine);
        }
    }

    private void observeServerStatus() {
        viewModel.getServerStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null) {
                status = MediaServerHub.ServerStatus.STOPPED;
            }

            boolean isWifiConnected = NetworkUtils.isWifiConnected(requireContext());

            switch (status) {
                case RUNNING:
                    btnStartServer.setEnabled(false);
                    btnStopServer.setEnabled(true);
                    tvServerAddress.setVisibility(VISIBLE);
                    qrCodeImage.setVisibility(VISIBLE);

                    tvServerStatus.setText(ONLINE_STATUS);
                    String serverLocation = viewModel.getServerLocationUrl();
                    tvServerAddress.setText(serverLocation);
                    detectWebEngine();
                    generateAndSetQRCode(serverLocation);
                    break;
                case STOPPED:
                case ERROR:
                    btnStartServer.setEnabled(isWifiConnected); // Only enable start if WiFi is on
                    btnStopServer.setEnabled(false);
                    qrCodeImage.setVisibility(GONE);

                    tvServerStatus.setText(OFFLINE_STATUS);
                    if (!isWifiConnected) {
                        tvServerAddress.setText(R.string.notification_server_not_running);
                        tvServerAddress.setVisibility(VISIBLE);
                    } else {
                        tvServerAddress.setVisibility(GONE);
                    }
                    break;
                case STARTING:
                    qrCodeImage.setVisibility(GONE);
                    tvServerAddress.setVisibility(GONE);
                    btnStartServer.setEnabled(false);
                    btnStopServer.setEnabled(false);
                    tvServerStatus.setText(OFFLINE_STATUS);
                    break;
            }
        });
    }

    /**
     * Generates a QR code from the given text and sets it to the ImageView.
     * @param text The text to encode in the QR code.
     */
    private void generateAndSetQRCode(String text) {
        if(text == null || text.isEmpty()) {
            qrCodeImage.setVisibility(GONE);
            return;
        }

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
