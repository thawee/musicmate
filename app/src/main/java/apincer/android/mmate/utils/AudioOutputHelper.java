package apincer.android.mmate.utils;

import static android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
import static apincer.music.core.utils.StringUtils.isEmpty;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioMixerAttributes;
import android.media.AudioProfile;
import android.os.Build;
import android.text.TextPaint;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import android.graphics.drawable.Drawable;

import java.lang.reflect.Method;
import java.util.List;

import apincer.android.mmate.R;
import apincer.music.core.model.Track;
import apincer.music.core.playback.DMRPlayer;
import apincer.music.core.playback.ExternalAndroidPlayer;
import apincer.music.core.playback.spi.PlaybackTarget;
import apincer.music.core.utils.StringUtils;
import apincer.music.core.utils.TagUtils;

public class AudioOutputHelper {
    private static final String TAG = AudioOutputHelper.class.getName();

    private static volatile String sCachedBtCodec = "";
    private static volatile int sCachedBtSampleRate = 0;
    private static volatile int sCachedBtBitsPerSample = 0;
    private static volatile long sLastBtRefreshTime = 0;
    private static final long BT_REFRESH_THROTTLE_MS = 2000;
    private static volatile BluetoothA2dp sBluetoothA2dp = null;

    public static class Device {
        private boolean bitPerfect;
        private boolean bluetooth;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setResId(int resId) {
            this.resId = resId;
        }

        public int getResId() {
            return resId;
        }

        public boolean isBluetooth() {
            return bluetooth;
        }

        public void setBluetooth(boolean bluetooth) {
            this.bluetooth = bluetooth;
        }

        String name;
        int bitPerSampling;
        long samplingRate;
        long bitRate;
        String description;

        public String getFriendyDescription() {
            StringBuilder builder = new StringBuilder();
            builder.append(getName());
            if (isBluetooth()) {
                if (!StringUtils.isEmpty(getCodec()) && !"PCM".equalsIgnoreCase(getCodec()) && !"-".equals(getCodec()) && !"A2DP".equalsIgnoreCase(getCodec())) {
                    builder.append(" • ").append(getCodec());
                }
            } else if (!StringUtils.isEmpty(getDescription())) {
                builder.append(" • ").append(getDescription());
                if (!StringUtils.isEmpty(getCodec()) && !"PCM".equalsIgnoreCase(getCodec()) && !"-".equals(getCodec())) {
                    builder.append(" (").append(getCodec()).append(")");
                }
            }
            if (getSamplingRate() > 0) {
                builder.append("\n");
                String res = TagUtils.formatResolution(getBitPerSampling(), getSamplingRate(), -1);
                builder.append(res);
            }
            return builder.toString();
        }

        public String getCompactLabel() {
            StringBuilder sb = new StringBuilder(getName());
            if (isBluetooth()) {
                if (!StringUtils.isEmpty(getCodec()) && !"PCM".equalsIgnoreCase(getCodec()) && !"-".equals(getCodec()) && !"A2DP".equalsIgnoreCase(getCodec())) {
                    sb.append(" • ").append(getCodec());
                }
            } else if (!StringUtils.isEmpty(getDescription())) {
                sb.append(" • ").append(getDescription());
                if (!StringUtils.isEmpty(getCodec()) && !"PCM".equalsIgnoreCase(getCodec()) && !"-".equals(getCodec())) {
                    sb.append(" (").append(getCodec()).append(")");
                }
            }
            return sb.toString();
        }

        public int getBitPerSampling() {
            return bitPerSampling;
        }

        public void setBitPerSampling(int bitPerSampling) {
            this.bitPerSampling = bitPerSampling;
        }

        public long getSamplingRate() {
            return samplingRate;
        }

        public void setSamplingRate(long samplingRate) {
            this.samplingRate = samplingRate;
        }

        public String getCodec() {
            return codec;
        }

        public void setCodec(String codec) {
            this.codec = codec;
        }

        String codec = "";

        public void setAddress(String address) {
            this.address = address;
        }

        String address;
        int resId;

        public String getDescription() {
            return description;
        }

        public boolean isBitPerfect() {
            return bitPerfect;
        }

        public void setBitPerfect(boolean bitPerfect) {
            this.bitPerfect = bitPerfect;
        }
    }

    @SuppressLint("MissingPermission")
    public static Device getOutputDevice(Context context, Track track) {
        Device outputDevice = new Device();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        AudioDeviceInfo[] outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        AudioDeviceInfo selectedDevice = getAudioDevice(outputs);

        // Default setup if we have a device, or absolute fallback
        if (selectedDevice != null) {
            readResolutions(context, outputDevice, selectedDevice);
            int sampleRate = (track != null) ? (int) track.getAudioSampleRate() : 44100;
            outputDevice.setBitPerfect(isBitPerfect(context, selectedDevice, sampleRate));
            boolean isBt = isBluetoothDevice(selectedDevice);
            outputDevice.setBluetooth(isBt);

            if (isBt) {
                outputDevice.setDescription("Bluetooth Audio");
                outputDevice.setResId(R.drawable.ic_round_bluetooth_audio_24);
                if (sCachedBtCodec != null && !sCachedBtCodec.isEmpty()) {
                    outputDevice.setCodec(sCachedBtCodec);
                }
            } else {
                outputDevice.setDescription(typeToString(selectedDevice.getType()));
                outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
            }
            outputDevice.setName(String.valueOf(selectedDevice.getProductName()));
        } else {
            outputDevice.setCodec("SRC");
            outputDevice.setName("Phone Speaker");
            outputDevice.setBitPerSampling(16);
            outputDevice.setSamplingRate(48000);
            outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
            outputDevice.setDescription(Build.MODEL);
            outputDevice.setBluetooth(false);
        }

        return outputDevice;
    }

    public static boolean isExternalAppTarget(PlaybackTarget target) {
        return target instanceof ExternalAndroidPlayer && !"local".equalsIgnoreCase(target.getTargetId());
    }

    public static Drawable getTargetDrawable(Context context, PlaybackTarget target, Track track) {
        Device device = (context != null) ? getOutputDevice(context, track) : null;
        return getTargetDrawable(context, target, device);
    }

    public static Drawable getTargetDrawable(Context context, PlaybackTarget target, Device audioOutputDevice) {
        if (context == null) return null;

        if (target instanceof ExternalAndroidPlayer extPlayer && "local".equalsIgnoreCase(extPlayer.getTargetId())) {
            if (audioOutputDevice != null) {
                if (audioOutputDevice.isBitPerfect()) {
                    return ContextCompat.getDrawable(context, R.drawable.ic_baseline_usb_24);
                } else if (audioOutputDevice.getResId() != 0) {
                    return ContextCompat.getDrawable(context, audioOutputDevice.getResId());
                }
            }
            return ContextCompat.getDrawable(context, R.drawable.ic_round_speaker_24);
        } else if (target instanceof ExternalAndroidPlayer) {
            Drawable appIcon = ExternalAndroidPlayer.Factory.getAppIcon(context, target.getTargetId());
            if (appIcon != null) {
                return UIUtils.scaleDrawable(context, appIcon, 24);
            }
            return ContextCompat.getDrawable(context, R.drawable.ic_round_speaker_24);
        } else if (target != null && (target.isStreaming() || target instanceof DMRPlayer)) {
            return ContextCompat.getDrawable(context, R.drawable.ic_dlna);
        } else if (target == null && audioOutputDevice != null && audioOutputDevice.getResId() != 0) {
            return ContextCompat.getDrawable(context, audioOutputDevice.getResId());
        }

        return ContextCompat.getDrawable(context, R.drawable.ic_round_speaker_24);
    }

    public static synchronized void initializeBluetooth(Context context) {
        if (context == null || sBluetoothA2dp != null) return;
        try {
            BluetoothAdapter adapter = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
                if (manager != null) {
                    adapter = manager.getAdapter();
                }
            }
            if (adapter == null) {
                adapter = BluetoothAdapter.getDefaultAdapter();
            }

            if (adapter != null) {
                adapter.getProfileProxy(context.getApplicationContext(), new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        if (profile == BluetoothProfile.A2DP) {
                            sBluetoothA2dp = (BluetoothA2dp) proxy;
                            refreshBluetoothCodecStatus(context);
                        }
                    }

                    @Override
                    public void onServiceDisconnected(int profile) {
                        if (profile == BluetoothProfile.A2DP) {
                            sBluetoothA2dp = null;
                        }
                    }
                }, BluetoothProfile.A2DP);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to initialize BluetoothA2dp proxy: " + e.getMessage());
        }
    }

    public static synchronized void cleanupBluetooth(Context context) {
        if (sBluetoothA2dp != null && context != null) {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    adapter.closeProfileProxy(BluetoothProfile.A2DP, sBluetoothA2dp);
                }
            } catch (Exception ignored) {}
            sBluetoothA2dp = null;
        }
    }

    public static synchronized void clearCachedBluetoothCodec() {
        sCachedBtCodec = "";
        sCachedBtSampleRate = 0;
        sCachedBtBitsPerSample = 0;
    }

    public static synchronized void refreshBluetoothCodecStatus(Context context) {
        refreshBluetoothCodecStatus(context, false);
    }

    public static synchronized void refreshBluetoothCodecStatus(Context context, boolean force) {
        if (!force && (System.currentTimeMillis() - sLastBtRefreshTime < BT_REFRESH_THROTTLE_MS)) {
            return;
        }
        sLastBtRefreshTime = System.currentTimeMillis();

        if (sBluetoothA2dp == null) {
            if (context != null) {
                initializeBluetooth(context);
            }
            return;
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }

            List<BluetoothDevice> candidates = new java.util.ArrayList<>();
            try {
                Method getActiveDeviceMethod = sBluetoothA2dp.getClass().getMethod("getActiveDevice");
                getActiveDeviceMethod.setAccessible(true);
                BluetoothDevice dev = (BluetoothDevice) getActiveDeviceMethod.invoke(sBluetoothA2dp);
                if (dev != null) candidates.add(dev);
            } catch (Throwable ignored) {}

            try {
                List<BluetoothDevice> connected = sBluetoothA2dp.getConnectedDevices();
                if (connected != null) {
                    for (BluetoothDevice d : connected) {
                        if (!candidates.contains(d)) candidates.add(d);
                    }
                }
            } catch (Throwable ignored) {}

            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    java.util.Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                    if (bonded != null) {
                        for (BluetoothDevice d : bonded) {
                            if (sBluetoothA2dp.getConnectionState(d) == BluetoothProfile.STATE_CONNECTED) {
                                if (!candidates.contains(d)) candidates.add(d);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            if (candidates.isEmpty()) {
                clearCachedBluetoothCodec();
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                for (BluetoothDevice dev : candidates) {
                    Object codecStatus = null;
                    try {
                        Method getCodecStatusMethod = sBluetoothA2dp.getClass().getMethod("getCodecStatus", BluetoothDevice.class);
                        getCodecStatusMethod.setAccessible(true);
                        codecStatus = getCodecStatusMethod.invoke(sBluetoothA2dp, dev);
                    } catch (Throwable e) {
                        Log.d(TAG, "Bluetooth codec extraction notice: " + e.getMessage());
                    }

                    if (codecStatus != null) {
                        parseCodecStatus(codecStatus);
                        if (!sCachedBtCodec.isEmpty()) {
                            break;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            Log.d(TAG, "Bluetooth codec extraction notice: " + e.getMessage());
        }
    }

    public static void parseCodecStatus(Object codecStatus) {
        if (codecStatus == null) return;
        try {
            sCachedBtCodec = "";
            sCachedBtSampleRate = 0;
            sCachedBtBitsPerSample = 0;
            
            String rawStr = String.valueOf(codecStatus);
            Log.d(TAG, "Parsing BluetoothCodecStatus: " + rawStr);

            Object config = null;
            try {
                Method getCodecConfigMethod = codecStatus.getClass().getMethod("getCodecConfig");
                getCodecConfigMethod.setAccessible(true);
                config = getCodecConfigMethod.invoke(codecStatus);
            } catch (Exception e1) {
                try {
                    Method getCodecConfigMethod = codecStatus.getClass().getDeclaredMethod("getCodecConfig");
                    getCodecConfigMethod.setAccessible(true);
                    config = getCodecConfigMethod.invoke(codecStatus);
                } catch (Exception ignored) {}
            }

            if (config == null) {
                try {
                    java.lang.reflect.Field f = codecStatus.getClass().getDeclaredField("mCodecConfig");
                    f.setAccessible(true);
                    config = f.get(codecStatus);
                } catch (Exception ignored) {}
            }

            if (config != null) {
                // 1. Codec Name / Type
                String codecName = "";
                try {
                    Method getNameMethod = config.getClass().getMethod("getCodecName");
                    getNameMethod.setAccessible(true);
                    codecName = (String) getNameMethod.invoke(config);
                } catch (Exception ignored) {}

                int codecType = -1;
                try {
                    Method getCodecTypeMethod = config.getClass().getMethod("getCodecType");
                    getCodecTypeMethod.setAccessible(true);
                    codecType = (Integer) getCodecTypeMethod.invoke(config);
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Field f = config.getClass().getDeclaredField("mCodecType");
                        f.setAccessible(true);
                        codecType = f.getInt(config);
                    } catch (Exception ignored) {}
                }

                if (codecName != null && !codecName.isEmpty() && !codecName.equalsIgnoreCase("unknown")) {
                    sCachedBtCodec = codecName;
                } else if (codecType >= 0) {
                    sCachedBtCodec = switch (codecType) {
                        case 0 -> "SBC";
                        case 1 -> "AAC";
                        case 2 -> "aptX";
                        case 3 -> "aptX HD";
                        case 4 -> "LDAC";
                        case 5 -> "LC3";
                        case 6 -> "Opus";
                        case 7 -> "aptX Adaptive";
                        default -> {
                            if (codecType >= 1000000) yield "SSC";
                            yield "A2DP";
                        }
                    };
                }

                // 2. Sample Rate
                try {
                    Method getSampleRateMethod = config.getClass().getMethod("getSampleRate");
                    getSampleRateMethod.setAccessible(true);
                    int rateMask = (Integer) getSampleRateMethod.invoke(config);
                    sCachedBtSampleRate = parseSampleRateMask(rateMask);
                } catch (Exception e3) {
                    try {
                        java.lang.reflect.Field f = config.getClass().getDeclaredField("mSampleRate");
                        f.setAccessible(true);
                        sCachedBtSampleRate = parseSampleRateMask(f.getInt(config));
                    } catch (Exception ignored) {}
                }

                // 3. Bits per Sample
                try {
                    Method getBitsMethod = config.getClass().getMethod("getBitsPerSample");
                    getBitsMethod.setAccessible(true);
                    int bitsMask = (Integer) getBitsMethod.invoke(config);
                    sCachedBtBitsPerSample = parseBitsMask(bitsMask);
                } catch (Exception e4) {
                    try {
                        java.lang.reflect.Field f = config.getClass().getDeclaredField("mBitsPerSample");
                        f.setAccessible(true);
                        sCachedBtBitsPerSample = parseBitsMask(f.getInt(config));
                    } catch (Exception ignored) {}
                }
            }

            // Fallback string matching across all Android builds
            String fullStr = (rawStr + " " + (config != null ? String.valueOf(config) : "")).toUpperCase();
            if (sCachedBtCodec.isEmpty() || "A2DP".equalsIgnoreCase(sCachedBtCodec)) {
                if (fullStr.contains("LDAC") || fullStr.contains("CODECTYPE:4") || fullStr.contains("CODECTYPE=4") || fullStr.contains("MCODECTYPE=4")) {
                    sCachedBtCodec = "LDAC";
                } else if (fullStr.contains("APTX_HD") || fullStr.contains("APTX HD") || fullStr.contains("CODECTYPE:3") || fullStr.contains("CODECTYPE=3") || fullStr.contains("MCODECTYPE=3")) {
                    sCachedBtCodec = "aptX HD";
                } else if (fullStr.contains("APTX_ADAPTIVE") || fullStr.contains("APTX ADAPTIVE") || fullStr.contains("CODECTYPE:7") || fullStr.contains("CODECTYPE=7")) {
                    sCachedBtCodec = "aptX Adaptive";
                } else if (fullStr.contains("APTX") || fullStr.contains("CODECTYPE:2") || fullStr.contains("CODECTYPE=2")) {
                    sCachedBtCodec = "aptX";
                } else if (fullStr.contains("LC3") || fullStr.contains("CODECTYPE:5") || fullStr.contains("CODECTYPE=5")) {
                    sCachedBtCodec = "LC3";
                } else if (fullStr.contains("OPUS") || fullStr.contains("CODECTYPE:6") || fullStr.contains("CODECTYPE=6")) {
                    sCachedBtCodec = "Opus";
                } else if (fullStr.contains("SSC") || fullStr.contains("SCALABLE")) {
                    sCachedBtCodec = "SSC";
                } else if (fullStr.contains("AAC") || fullStr.contains("CODECTYPE:1") || fullStr.contains("CODECTYPE=1")) {
                    sCachedBtCodec = "AAC";
                } else if (fullStr.contains("SBC") || fullStr.contains("CODECTYPE:0") || fullStr.contains("CODECTYPE=0")) {
                    sCachedBtCodec = "SBC";
                }
            }

            if (sCachedBtSampleRate <= 0) {
                if (fullStr.contains("96000") || (sCachedBtCodec.equals("LDAC") && fullStr.contains("SAMPLE_RATE_96000"))) {
                    sCachedBtSampleRate = 96000;
                } else if (fullStr.contains("88200")) {
                    sCachedBtSampleRate = 88200;
                } else if (fullStr.contains("48000")) {
                    sCachedBtSampleRate = 48000;
                } else if (fullStr.contains("44100")) {
                    sCachedBtSampleRate = 44100;
                } else if ("LDAC".equalsIgnoreCase(sCachedBtCodec)) {
                    sCachedBtSampleRate = 96000;
                }
            }

            if (sCachedBtBitsPerSample <= 0) {
                if (fullStr.contains("24BIT") || fullStr.contains("24-BIT") || fullStr.contains("BITS_24") || fullStr.contains("BITS_PER_SAMPLE_24") || "LDAC".equalsIgnoreCase(sCachedBtCodec) || "aptX HD".equalsIgnoreCase(sCachedBtCodec)) {
                    sCachedBtBitsPerSample = 24;
                } else if (fullStr.contains("32BIT") || fullStr.contains("32-BIT")) {
                    sCachedBtBitsPerSample = 32;
                } else {
                    sCachedBtBitsPerSample = 16;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to parse Bluetooth codec config: " + e.getMessage());
        }
    }

    private static int parseSampleRateMask(int rateMask) {
        return switch (rateMask) {
            case 1 << 0 -> 44100;
            case 1 << 1 -> 48000;
            case 1 << 2 -> 88200;
            case 1 << 3 -> 96000;
            case 1 << 4 -> 176400;
            case 1 << 5 -> 192000;
            default -> (rateMask > 1000) ? rateMask : 0;
        };
    }

    private static int parseBitsMask(int bitsMask) {
        return switch (bitsMask) {
            case 1 << 0 -> 16;
            case 1 << 1 -> 24;
            case 1 << 2 -> 32;
            default -> (bitsMask >= 8 && bitsMask <= 32) ? bitsMask : 0;
        };
    }

    public static final int CODEC_TYPE_SBC = 0;
    public static final int CODEC_TYPE_AAC = 1;
    public static final int CODEC_TYPE_APTX = 2;
    public static final int CODEC_TYPE_APTX_HD = 3;
    public static final int CODEC_TYPE_LDAC = 4;
    public static final int CODEC_TYPE_LC3 = 5;
    public static final int CODEC_TYPE_OPUS = 6;
    public static final int CODEC_TYPE_APTX_ADAPTIVE = 7;

    public static boolean setCodecPreference(Context context, int targetCodec) {
        if (sBluetoothA2dp == null && context != null) {
            initializeBluetooth(context);
        }
        if (sBluetoothA2dp == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // setCodecConfigPreference requires BLUETOOTH_PRIVILEGED on Android 13+
            return false;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }

            BluetoothDevice activeDev = null;
            try {
                Method getActiveDeviceMethod = BluetoothA2dp.class.getDeclaredMethod("getActiveDevice");
                getActiveDeviceMethod.setAccessible(true);
                activeDev = (BluetoothDevice) getActiveDeviceMethod.invoke(sBluetoothA2dp);
            } catch (Exception ignored) {}

            if (activeDev == null) {
                List<BluetoothDevice> connected = sBluetoothA2dp.getConnectedDevices();
                if (connected != null && !connected.isEmpty()) {
                    activeDev = connected.get(0);
                }
            }

            if (activeDev == null) return false;

            int sampleRate = (targetCodec == CODEC_TYPE_LDAC) ? (1 << 3) : (1 << 1); // 96kHz for LDAC, 48kHz for others
            int bitsPerSample = (targetCodec == CODEC_TYPE_LDAC || targetCodec == CODEC_TYPE_APTX_HD) ? (1 << 1) : (1 << 0); // 24-bit for LDAC/aptX HD
            int channelMode = 1 << 1; // Stereo
            int priority = 1000000; // CODEC_PRIORITY_HIGHEST

            Class<?> configClass = Class.forName("android.bluetooth.BluetoothCodecConfig");
            Object config = null;

            try {
                java.lang.reflect.Constructor<?> ctor = configClass.getConstructor(
                        int.class, int.class, int.class, int.class, int.class,
                        long.class, long.class, long.class, long.class
                );
                config = ctor.newInstance(targetCodec, priority, sampleRate, bitsPerSample, channelMode, 0L, 0L, 0L, 0L);
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Constructor<?> ctor2 = configClass.getConstructor(
                            int.class, int.class, int.class, int.class, int.class,
                            int.class, int.class, int.class, int.class
                    );
                    config = ctor2.newInstance(targetCodec, priority, sampleRate, bitsPerSample, channelMode, 0, 0, 0, 0);
                } catch (Exception ignored) {}
            }

            if (config != null) {
                Method setCodecConfigMethod = BluetoothA2dp.class.getDeclaredMethod("setCodecConfigPreference", BluetoothDevice.class, configClass);
                setCodecConfigMethod.setAccessible(true);
                setCodecConfigMethod.invoke(sBluetoothA2dp, activeDev, config);
                Log.d(TAG, "Successfully requested Bluetooth codec preference: " + targetCodec);
                refreshBluetoothCodecStatus(context);
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set Bluetooth codec preference via reflection: " + e.getMessage());
        }
        return false;
    }

    public static void autoOptimizeBluetoothCodec(Context context) {
        if (context == null) return;
        try {
            // Automatically request highest quality LDAC / aptX HD priority in background
            setCodecPreference(context, CODEC_TYPE_LDAC);
        } catch (Exception ignored) {}
    }

    public static void openSystemAudioOrBluetooth(Context context) {
        if (context == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent intent = new Intent("com.android.settings.panel.action.MEDIA_OUTPUT");
                intent.putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.getPackageName());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }
        } catch (Exception ignored) {}
        openBluetoothSettings(context);
    }

    public static void openBluetoothSettings(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to launch Bluetooth settings", e);
        }
    }

    public static void openDeveloperSettings(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to launch Developer settings", e);
            openBluetoothSettings(context);
        }
    }

    public static String getBluetoothCodec(Context context) {
        return sCachedBtCodec != null ? sCachedBtCodec : "";
    }

    public static int getBluetoothSampleRate(Context context) {
        return sCachedBtSampleRate;
    }

    public static int getBluetoothBitsPerSample(Context context) {
        return sCachedBtBitsPerSample;
    }

    private static boolean isBluetoothDevice(AudioDeviceInfo device) {
        if (device == null) return false;
        int type = device.getType();
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER;
        }
        return false;
    }

    // Check if the current path is truly bit-perfect (Android 14+)
    public static boolean isBitPerfect(Context context, AudioDeviceInfo device, int trackSampleRate) {
        if (context == null || device == null) return false;
        int type = device.getType();
        // Bit-perfect mixer attributes are only supported on USB audio sinks
        if (type != AudioDeviceInfo.TYPE_USB_DEVICE &&
            type != AudioDeviceInfo.TYPE_USB_HEADSET &&
            type != AudioDeviceInfo.TYPE_USB_ACCESSORY) {
            return false;
        }

        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                List<AudioMixerAttributes> mixerAttrs = am.getSupportedMixerAttributes(device);
                if (mixerAttrs != null) {
                    for (AudioMixerAttributes attr : mixerAttrs) {
                        if (attr.getMixerBehavior() == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT) {
                            if (attr.getFormat().getSampleRate() == trackSampleRate) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static AudioDeviceInfo getAudioDevice(AudioDeviceInfo[] devices) {
        AudioDeviceInfo bestDevice = null;
        int highestPriority = -1;

        for (AudioDeviceInfo device : devices) {
            if (!device.isSink()) continue;

            int type = device.getType();
            int priority = getDevicePriority(type);

            if (priority > highestPriority) {
                highestPriority = priority;
                bestDevice = device;
            }
        }
        return bestDevice;
    }

    private static int getDevicePriority(int type) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                return 3;
            }
        }
        return switch (type) {
            case AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
                 AudioDeviceInfo.TYPE_USB_ACCESSORY -> 4;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 3;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> 1;
            case TYPE_BUILTIN_SPEAKER -> 0;
            default -> -1;
        };
    }

    static String typeToString(int type) {
        return switch (type) {
            case AudioDeviceInfo.TYPE_AUX_LINE -> "AUX Line Out";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in Earpiece";
            case AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic";
            case TYPE_BUILTIN_SPEAKER -> "Phone Speaker";
            case AudioDeviceInfo.TYPE_BUS -> "Audio Bus";
            case AudioDeviceInfo.TYPE_DOCK -> "Audio Dock";
            case AudioDeviceInfo.TYPE_FM -> "FM Transmitter";
            case AudioDeviceInfo.TYPE_FM_TUNER -> "FM Tuner";
            case AudioDeviceInfo.TYPE_HDMI -> "HDMI Audio";
            case AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI eARC";
            case AudioDeviceInfo.TYPE_IP -> "IP Audio";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Digital Line Out";
            case AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony";
            case AudioDeviceInfo.TYPE_TV_TUNER -> "TV Output";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory";
            case AudioDeviceInfo.TYPE_USB_DEVICE -> "USB DAC";
            case AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset";
            default -> "System Output";
        };
    }

    private static void readResolutions(Context context, Device outputDevice, AudioDeviceInfo device) {
        boolean isBt = isBluetoothDevice(device);
        String detectedCodec = "";
        int rate = 0;
        int bps = 0;

        if (isBt && context != null) {
            refreshBluetoothCodecStatus(context);
            detectedCodec = getBluetoothCodec(context);
            rate = getBluetoothSampleRate(context);
            bps = getBluetoothBitsPerSample(context);
        }

        if (detectedCodec.isEmpty() || "A2DP".equalsIgnoreCase(detectedCodec)) {
            String hwCodec = detectCodec(device);
            if (!hwCodec.isEmpty() && !"PCM".equalsIgnoreCase(hwCodec)) {
                detectedCodec = hwCodec;
            } else if (isBt) {
                detectedCodec = !detectedCodec.isEmpty() ? detectedCodec : "A2DP";
            } else {
                detectedCodec = "PCM";
            }
        }
        outputDevice.setCodec(detectedCodec);

        // 2. Get Bit Depth
        if (bps <= 0) {
            int[] encodings = device.getEncodings();
            int highestEncoding = getHighestEncoding(encodings);
            bps = switch (highestEncoding) {
                case AudioFormat.ENCODING_PCM_8BIT -> 8;
                case AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24;
                case AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32;
                default -> 16;
            };
        }
        if ("LDAC".equalsIgnoreCase(detectedCodec) || "aptX HD".equalsIgnoreCase(detectedCodec) || "SSC".equalsIgnoreCase(detectedCodec)) {
            bps = Math.max(bps, 24);
        }
        outputDevice.setBitPerSampling(bps);

        // 3. Get Sample Rate
        if (rate <= 0) {
            List<AudioProfile> profiles = device.getAudioProfiles();
            for (AudioProfile profile : profiles) {
                for (int sampleRate : profile.getSampleRates()) {
                    if (sampleRate > rate) rate = sampleRate;
                }
            }
            if (rate == 0) {
                for (int sampleRate : device.getSampleRates()) {
                    if (sampleRate > rate) rate = sampleRate;
                }
            }
            if (rate == 0 && ("LDAC".equalsIgnoreCase(detectedCodec) || "SSC".equalsIgnoreCase(detectedCodec))) {
                rate = 96000;
            }
            if (rate == 0) rate = 48000;
        }

        outputDevice.setSamplingRate(rate);
    }

    private static String detectCodec(AudioDeviceInfo device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (AudioProfile profile : device.getAudioProfiles()) {
                int format = profile.getFormat();
                String codec = formatToCodecName(format);
                if (!codec.isEmpty() && !codec.equals("PCM")) {
                    return codec;
                }
            }
        }
        for (int enc : device.getEncodings()) {
            String codec = formatToCodecName(enc);
            if (!codec.isEmpty() && !codec.equals("PCM")) {
                return codec;
            }
        }
        if (isBluetoothDevice(device)) {
            return "A2DP";
        }
        return "PCM";
    }

    private static String formatToCodecName(int format) {
        return switch (format) {
            case AudioFormat.ENCODING_AAC_LC, AudioFormat.ENCODING_AAC_HE_V1, AudioFormat.ENCODING_AAC_HE_V2, AudioFormat.ENCODING_AAC_ELD, AudioFormat.ENCODING_AAC_XHE -> "AAC";
            case AudioFormat.ENCODING_OPUS -> "Opus";
            case AudioFormat.ENCODING_AC3 -> "AC3";
            case AudioFormat.ENCODING_E_AC3 -> "E-AC3";
            case AudioFormat.ENCODING_DTS -> "DTS";
            case AudioFormat.ENCODING_DTS_HD -> "DTS-HD";
            case AudioFormat.ENCODING_DOLBY_TRUEHD -> "TrueHD";
            case AudioFormat.ENCODING_IEC61937 -> "SPDIF";
            default -> {
                // Check for Bluetooth codec constant values across API levels
                if (format == 26) yield "LDAC";
                if (format == 27) yield "aptX";
                if (format == 28) yield "aptX HD";
                if (format == 29) yield "LC3";
                if (format == 30) yield "aptX Adaptive";
                if (format == 31) yield "aptX TWSP";
                yield "";
            }
        };
    }

    /** Helper to get the most "Audiophile" encoding available **/
    private static int getHighestEncoding(int[] encodings) {
        int best = AudioFormat.ENCODING_PCM_16BIT;
        for (int e : encodings) {
            if (e == AudioFormat.ENCODING_PCM_FLOAT || e == AudioFormat.ENCODING_PCM_32BIT) return e;
            if (e == AudioFormat.ENCODING_PCM_24BIT_PACKED) best = e;
        }
        return best;
    }

    /** Helper to convert Format IDs to Readable Names for your UI **/
    private static String getFormatName(int format) {
        return switch (format) {
            case AudioFormat.ENCODING_PCM_FLOAT -> "PCM (Float)";
            case AudioFormat.ENCODING_DTS, AudioFormat.ENCODING_DTS_HD -> "DTS";
            case AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3 -> "Dolby Digital";
            default -> "PCM";
        };
    }

    public static Bitmap getOutputDeviceIcon(Context context, Device dev) {
        int width = 128;  // 16x21, 24x32
        int height = 96;

        int darkGreyColor = context.getColor(R.color.grey900);
        int whiteColor = context.getColor(R.color.white);
        int blackColor = context.getColor(R.color.black);
        String codec = dev.getCodec();
        if(codec !=null && codec.length()>6) {
            codec = codec.substring(0,6);
        }
        codec = StringUtils.trimToEmpty(codec);
        if(isEmpty(codec)) {
            codec = "-";
        }

        String rate =  StringUtils.formatAudioSampleRate(dev.getSamplingRate(),true);

        Bitmap myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas myCanvas = new Canvas(myBitmap);
        int padding = 2;
        int cornerRadius = 4;
        Rect bounds = new Rect(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        // Initialize a new Round Rect object
        // draw outer dark grey block
        RectF rectangle = new RectF(
                0, // Left
                0, // Top
                myCanvas.getWidth(), // Right
                myCanvas.getHeight() // Bottom
        );

        Paint bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(darkGreyColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw black box
        padding = 4;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                myCanvas.getHeight() - padding // Bottom
        );

        bgPaint =  new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(blackColor);
        bgPaint.setStyle(Paint.Style.FILL);
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, bgPaint);

        // draw top white box
        padding = 12;
        rectangle = new RectF(
                padding, // Left
                padding, // Top
                myCanvas.getWidth() - padding, // Right
                (myCanvas.getHeight()/2) - 2 // Bottom
        );

        Paint paint =  new Paint();
        paint.setAntiAlias(true);
        paint.setColor(whiteColor);
        paint.setStyle(Paint.Style.FILL);
        // Finally, draw the rectangle on the canvas
        myCanvas.drawRoundRect(rectangle, cornerRadius,cornerRadius, paint);

        int letterTextSize = 28;
        Typeface font =  ResourcesCompat.getFont(context, R.font.adca);

        // draw bit per , black color
        Paint mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(blackColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        Rect textMathRect = new Rect();
        mLetterPaint.getTextBounds(codec, 0, 1, textMathRect);
        float mLetterTop = textMathRect.height() / 10f;
        float mPositionY= bounds.exactCenterY()-(bounds.exactCenterY()/4);
        myCanvas.drawText(codec,
                bounds.exactCenterX(), mLetterTop + mPositionY,
                mLetterPaint);

        // draw sampling rate, white
        letterTextSize = 34;
        font =  ResourcesCompat.getFont(context, R.font.oswald_bold);
        mLetterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mLetterPaint.setColor(whiteColor);
        mLetterPaint.setTypeface(font);
        mLetterPaint.setTextSize(letterTextSize);
        mLetterPaint.setTextAlign(Paint.Align.CENTER);
        // Text draws from the baselineAdd some top padding to center vertically.
        textMathRect = new Rect();
        mLetterPaint.getTextBounds(rate, 0, 1, textMathRect);
        mLetterTop = mLetterTop +(textMathRect.height() / 2f);
        mPositionY= bounds.exactCenterY()+(bounds.exactCenterY()/3);
        myCanvas.drawText(rate,
                bounds.exactCenterX(), mLetterTop + mPositionY+6,
                mLetterPaint);

        return myBitmap;
    }

    /**
     * Converts an integer array into a string where each int is separated by a space
     *
     * @param integerArray the integer array to convert to a string
     * @return string containing all the integer values separated by spaces
     */
    private static String intArrayToString(int[] integerArray){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < integerArray.length; i++){
            sb.append(integerArray[i]);
            if (i != integerArray.length -1) sb.append(" ");
        }
        return sb.toString();
    }
    private static int intArrayLastIndex(int[] integerArray){
        if(integerArray==null || integerArray.length==0) return 0;
        return integerArray[integerArray.length-1];
    }

}