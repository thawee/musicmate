import re

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

# 1. Add import
if "import apincer.android.mmate.ui.compose.DialogInterop;" not in content:
    content = content.replace(
        "import apincer.android.mmate.ui.viewmodel.MediaServerViewModel;",
        "import apincer.android.mmate.ui.viewmodel.MediaServerViewModel;\nimport apincer.android.mmate.ui.compose.DialogInterop;\nimport apincer.android.mmate.ui.compose.MediaServerState;"
    )

# 2. Add MediaServerState field
if "private MediaServerState mediaServerState;" not in content:
    content = content.replace(
        "private MediaServerViewModel mediaServerViewModel;",
        "private MediaServerViewModel mediaServerViewModel;\n    private MediaServerState mediaServerState = new MediaServerState();"
    )

# 3. Replace inflation
inflate_target = "viewMediaServerPage = LayoutInflater.from(getContext()).inflate(R.layout.view_action_server_management_bottom_sheet, null);"
inflate_replacement = """
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        String initialEngine = prefs.getString(Constants.PREF_SERVER_ENGINE, "httpcore");
        mediaServerState.setCurrentEngine(initialEngine);
        mediaServerState.setEngineDescription(getEngineDescription(initialEngine));

        viewMediaServerPage = DialogInterop.createMediaServerPageView(
            requireContext(),
            mediaServerState,
            engine -> {
                String prevEngine = prefs.getString(Constants.PREF_SERVER_ENGINE, "httpcore");
                if (!engine.equals(prevEngine)) {
                    prefs.edit().putString(Constants.PREF_SERVER_ENGINE, engine).apply();
                    mediaServerState.setCurrentEngine(engine);
                    mediaServerState.setEngineDescription(getEngineDescription(engine));
                    mediaServerViewModel.restartServer();
                    Toast.makeText(getContext(), "Switching engine — restarting server…", Toast.LENGTH_SHORT).show();
                }
            },
            () -> mediaServerViewModel.startServer(),
            () -> mediaServerViewModel.stopServer(),
            () -> {
                String url = mediaServerState.getServerUrl();
                if (url != null && !url.isEmpty()) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Server URL", url);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(getContext(), "Server URL copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                }
            },
            () -> {
                String url = mediaServerState.getServerUrl();
                if (url != null && url.startsWith("http")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Could not open browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            },
            () -> Toast.makeText(getContext(), "Scan with phone or tablet to open WebUI", Toast.LENGTH_SHORT).show()
        );
"""
content = content.replace(inflate_target, inflate_replacement)

# 4. Extract getEngineDescription from updateEngineDescription
if "private String getEngineDescription(String engine)" not in content:
    content = content.replace(
        "private void updateEngineDescription(String engine) {",
        "private String getEngineDescription(String engine) {\n        switch (engine) {\n            case \"nio\":\n                return \"⚡ Ultra-low latency • Minimal battery & RAM footprint\";\n            case \"netty\":\n                return \"🚀 High concurrency • Zero-copy file streaming\";\n            default:\n                return \"🛡️ Apache Async Reactor • Maximum network resilience\";\n        }\n    }\n\n    private void updateEngineDescription(String engine) {"
    )

# 5. Null out setupMediaServerTab
setup_media_start = content.find("private void setupMediaServerTab(View view) {")
if setup_media_start != -1:
    brace_count = 0
    setup_media_end = -1
    for i in range(setup_media_start, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                setup_media_end = i + 1
                break
    
    if setup_media_end != -1:
        content = content[:setup_media_start] + "private void setupMediaServerTab(View view) { /* Handled by Compose */ }" + content[setup_media_end:]

# 6. Update updateServerUI to use state instead of Views
update_ui_start = content.find("private void updateServerUI(MediaServerHub.ServerStatus status) {")
if update_ui_start != -1:
    brace_count = 0
    update_ui_end = -1
    for i in range(update_ui_start, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                update_ui_end = i + 1
                break
                
    new_update_ui = """private void updateServerUI(MediaServerHub.ServerStatus status) {
        if (getContext() == null) return;
        boolean isNetworkAvailable = NetworkUtils.isWifiConnected(requireContext()) || NetworkUtils.isHotspotActive(requireContext());
        mediaServerState.setNetworkAvailable(isNetworkAvailable);
        
        switch (status) {
            case RUNNING:
                mediaServerState.setServerRunning(true);
                mediaServerState.setBroadcastInfo("DLNA 1.5 / UPnP AV • Active on Port 9000");

                String ssid = ApplicationUtils.getWifiSSID(getContext());
                if (!StringUtils.isEmpty(ssid)) {
                    mediaServerState.setServerStatusText(SERVER_STATUS_ONLINE_PREFIX + " (" + ssid + ")");
                } else if (NetworkUtils.isHotspotActive(requireContext())) {
                    mediaServerState.setServerStatusText(SERVER_STATUS_ONLINE_PREFIX + " (Hotspot)");
                } else {
                    mediaServerState.setServerStatusText(SERVER_STATUS_ONLINE);
                }

                String serverLocation = mediaServerViewModel.getServerURL(getContext());
                mediaServerState.setServerUrl(serverLocation);
                mediaServerState.setQrCodeBitmap(generateQRCode(serverLocation));
                break;

            case STOPPED:
            case ERROR:
                mediaServerState.setServerRunning(false);
                mediaServerState.setBroadcastInfo("");
                mediaServerState.setQrCodeBitmap(null);
                mediaServerState.setServerStatusText(SERVER_STATUS_OFFLINE);
                mediaServerState.setServerUrl(isNetworkAvailable ? getString(R.string.server_url_not_available) : getString(R.string.notification_server_not_running));
                break;

            case STARTING:
                mediaServerState.setServerRunning(false);
                mediaServerState.setBroadcastInfo("");
                mediaServerState.setQrCodeBitmap(null);
                mediaServerState.setServerUrl("");
                mediaServerState.setServerStatusText(SERVER_STATUS_OFFLINE);
                break;
        }
    }"""
    content = content[:update_ui_start] + new_update_ui + content[update_ui_end:]

# 7. Replace generateAndSetQRCode with generateQRCode
generate_qr_start = content.find("private void generateAndSetQRCode(String text) {")
if generate_qr_start != -1:
    brace_count = 0
    generate_qr_end = -1
    for i in range(generate_qr_start, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                generate_qr_end = i + 1
                break
                
    new_qr = """private Bitmap generateQRCode(String text) {
        if (text == null || text.isEmpty()) return null;
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
            return bmp;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }"""
    content = content[:generate_qr_start] + new_qr + content[generate_qr_end:]

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
print("Patched AudioHubBottomSheet.java")
