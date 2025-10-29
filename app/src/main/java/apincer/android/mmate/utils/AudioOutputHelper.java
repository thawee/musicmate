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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.os.Build;
import android.text.TextPaint;
import android.util.Log;

import androidx.core.content.res.ResourcesCompat;

import java.util.HashMap;
import java.util.List;

import apincer.android.mmate.R;
import apincer.music.core.utils.StringUtils;

public class AudioOutputHelper {
    private static final String TAG = AudioOutputHelper.class.getName();

    public static class Device {
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

        String name;
        int bitPerSampling;
        long samplingRate;
        long bitRate;
        String description;

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
    }

    public interface Callback {
        void onReady(Device device);
    }

    @SuppressLint("MissingPermission")
    public static void getOutputDevice(Context context, Callback callback) {
        Device outputDevice = new Device();
        MediaRouter mr = (MediaRouter) context.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        MediaRouter.RouteInfo ri = mr.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        int devicetype = ri.getDeviceType();
        String deviceName = String.valueOf(ri.getName()).toLowerCase();
        String desc = ri.getDescription()==null?String.valueOf(ri.getName()):String.valueOf(ri.getDescription());
        String deviceDesc = desc.toLowerCase();

        AudioDeviceInfo[] adi = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
       // boolean foundDevice = false;

        for (AudioDeviceInfo a : adi) {
            // Check if this is the active output device
            if (a.isSink()) {
                if (isUSBDevice(devicetype, deviceName) || isHDMIDevice(devicetype, deviceName)) {
                    // USB or HDMI device
                    setResolutions(outputDevice, a, desc);
                    outputDevice.setResId(R.drawable.ic_baseline_usb_24);
                    outputDevice.setAddress(a.getAddress());
                    UsbManager usb_manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                    HashMap<String, UsbDevice> deviceList = usb_manager.getDeviceList();
                    outputDevice.setCodec("Direct");

                    for (UsbDevice device : deviceList.values()) {
                        outputDevice.setName(device.getProductName());
                        break; // Use the first USB device found
                    }

                    callback.onReady(outputDevice);
                    return; // Exit after finding USB device
                } else if (isBluetoothDevice(devicetype, deviceName, deviceDesc)) {
                    // Bluetooth device
                    outputDevice.setAddress(a.getAddress());
                    outputDevice.setResId(R.drawable.ic_round_bluetooth_audio_24);
                    getA2DP(context, outputDevice, callback, desc);
                    callback.onReady(outputDevice);
                    return; // A2DP callback will handle the response
                } else if (isWiredDevice(devicetype)) {
                    // Wired headphones/headset
                    setResolutions(outputDevice, a, desc);
                    outputDevice.setCodec("SRC");
                    outputDevice.setName(Build.MODEL);
                    outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
                    callback.onReady(outputDevice);
                    return;
                }
            }
        }

        // If no specific device was found, use the default device
            // Find the first available output device
            for (AudioDeviceInfo a : adi) {
                if (a.isSink()) {
                    setResolutions(outputDevice, a, desc);
                    outputDevice.setCodec("SRC*");
                    outputDevice.setName(Build.MODEL);
                    outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
                    callback.onReady(outputDevice);
                    return;
                }
            }

        // If we get here, no output device was found at all
        // Create a default device with basic information
        outputDevice.setCodec("SRC**");
        outputDevice.setName(Build.MODEL);
        outputDevice.setBitPerSampling(16);
        outputDevice.setSamplingRate(48000);
        outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
        outputDevice.setDescription(desc);
        callback.onReady(outputDevice);
    }

    private static boolean isHDMIDevice(int devicetype, String deviceName) {
        return (devicetype == AudioDeviceInfo.TYPE_HDMI ||
                devicetype == AudioDeviceInfo.TYPE_HDMI_ARC) ||
                ((isWiredDevice(devicetype)) &&
                        deviceName.contains("hdmi"));
    }

    private static boolean isUSBDevice(int devicetype, String deviceName) {
        return (devicetype == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                devicetype == AudioDeviceInfo.TYPE_USB_DEVICE) ||
                ((isWiredDevice(devicetype)) &&
                        deviceName.contains("usb"));
    }

    private static boolean isWiredDevice(int devicetype) {
        return devicetype == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                devicetype == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                devicetype == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                devicetype ==AudioDeviceInfo.TYPE_UNKNOWN;
    }

    private static boolean isBluetoothDevice(int devicetype, String name, String deviceName) {
        return (devicetype == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                devicetype == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) ||
                ((isWiredDevice(devicetype)) && (deviceName.contains("bluetooth")) || name.contains("bluetooth"));
    }

    /**
     * Converts an {@link AudioDeviceInfo} object into a human readable representation
     *
     * @param adi The AudioDeviceInfo object to be converted to a String
     * @return String containing all the information from the AudioDeviceInfo object
     */
    public static String toString(AudioDeviceInfo adi) {
        StringBuilder sb = new StringBuilder();
        sb.append("Id: ");
        sb.append(adi.getId());
        sb.append("\nProduct name: ");
        sb.append(adi.getProductName());
        sb.append("\nType: ");
        sb.append(typeToString(adi.getType()));
        sb.append("\nIs source: ");
        sb.append((adi.isSource() ? "Yes" : "No"));
        sb.append("\nIs sink: ");
        sb.append((adi.isSink() ? "Yes" : "No"));
        sb.append("\nChannel counts: ");
        int[] channelCounts = adi.getChannelCounts();
        sb.append(intArrayToString(channelCounts));
        sb.append("\nChannel masks: ");
        int[] channelMasks = adi.getChannelMasks();
        sb.append(intArrayToString(channelMasks));
        sb.append("\nChannel index masks: ");
        int[] channelIndexMasks = adi.getChannelIndexMasks();
        sb.append(intArrayToString(channelIndexMasks));
        sb.append("\nEncodings: ");
        int[] encodings = adi.getEncodings();
        sb.append(intArrayToString(encodings));
        sb.append("\nSample Rates: ");
        int[] sampleRates = adi.getSampleRates();
        sb.append(intArrayToString(sampleRates));
        return sb.toString();
    }

    /**
     * Converts the value from {@link AudioDeviceInfo#getType()} into a human
     * readable string
     * @param type One of the {@link AudioDeviceInfo}.TYPE_* values
     *             e.g. AudioDeviceInfo.TYPE_BUILT_IN_SPEAKER
     * @return string which describes the type of audio device
     */
    static String typeToString(int type){
        return switch (type) {
            case AudioDeviceInfo.TYPE_AUX_LINE -> "aux line-level connectors";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth device w/ A2DP profile";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth device w/ telephony";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "built-in earphone speaker";
            case AudioDeviceInfo.TYPE_BUILTIN_MIC -> "built-in microphone";
            case TYPE_BUILTIN_SPEAKER -> "built-in speaker";
            case AudioDeviceInfo.TYPE_BUS -> "BUS";
            case AudioDeviceInfo.TYPE_DOCK -> "DOCK";
            case AudioDeviceInfo.TYPE_FM -> "FM";
            case AudioDeviceInfo.TYPE_FM_TUNER -> "FM tuner";
            case AudioDeviceInfo.TYPE_HDMI -> "HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI audio return channel";
            case AudioDeviceInfo.TYPE_IP -> "IP";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL -> "line digital";
            case AudioDeviceInfo.TYPE_TELEPHONY -> "telephony";
            case AudioDeviceInfo.TYPE_TV_TUNER -> "TV tuner";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory";
            case AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset";
            default -> "";
        };
    }

    private static void setResolutions(Device outputDevice, AudioDeviceInfo device, String desc) {
        int encoding = intArrayLastIndex(device.getEncodings());
        int bps = switch (encoding) {
            case AudioFormat.ENCODING_PCM_8BIT -> 8;
            case AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24;
            case AudioFormat.ENCODING_PCM_32BIT -> 32;
            default -> 16;
        };
        outputDevice.setBitPerSampling(bps);
        device.getChannelCounts();
        // Get the highest supported sample rate instead of just the last one
        int[] sampleRates = device.getSampleRates();
        int rate = 0;
        // Find the highest sample rate (usually better quality)
        for (int sampleRate : sampleRates) {
            if (sampleRate > rate) {
                rate = sampleRate;
            }
        }

        // If no sample rate found, use a default value
        if (rate == 0) {
            rate = 48000; // Default to 48kHz if no sample rate information available
        }
        outputDevice.setSamplingRate(rate);
        outputDevice.setDescription(desc);
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

    public static void getA2DP(Context context, Device device, Callback callback, String desc) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {

            @SuppressLint("MissingPermission")
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                List<BluetoothDevice> devices = proxy.getConnectedDevices();
                BluetoothA2dp bA2dp = (BluetoothA2dp) proxy;
                for (BluetoothDevice dev : devices) {
                        device.setName(dev.getName());
                        getA2DPCodec(bA2dp, dev, device, desc);
                        callback.onReady(device);
                        break;
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {

            }
        }, BluetoothProfile.A2DP);
    }

    @SuppressLint("PrivateApi")
    private static void getA2DPCodec(BluetoothA2dp bA2dp, BluetoothDevice device, Device device1, String desc) {
        device1.setCodec("");
        device1.setDescription("N/A");
        if (bA2dp == null || device == null) return ;
        try {
            Class<?> cls = Class.forName("android.bluetooth.BluetoothA2dp");
            Class[] attrs = new Class[]{BluetoothDevice.class};
            Object object = cls.getMethod("getCodecStatus", attrs).invoke(bA2dp, device);
            if (object == null) return ;

            cls = Class.forName("android.bluetooth.BluetoothCodecStatus");
            object = cls.getMethod("getCodecConfig").invoke(object);
            if (object == null) return ;

            //{codecName:AAC,mCodecType:1,mCodecPriority:100000,mSampleRate:0x1(44100),mBitsPerSample:0x1(16),mChannelMode:0x2(STEREO),mCodecSpecific1:0,mCodecSpecific2:0,mCodecSpecific3:0,mCodecSpecific4:0}
            String text = object.toString();
            int startIndex = text.indexOf("codecName:");
            int endIndex = text.indexOf(",",startIndex);
            String codecName = text.substring(startIndex+10, endIndex);
            device1.setCodec(codecName);

            startIndex = text.indexOf("mSampleRate:");
            startIndex = text.indexOf("(", startIndex);
            endIndex = text.indexOf(")",startIndex);
            String sampleRate = text.substring(startIndex+1, endIndex);

            startIndex = text.indexOf("mBitsPerSample:");
            startIndex = text.indexOf("(", startIndex);
            endIndex = text.indexOf(")",startIndex);
            String bitsPerSample = text.substring(startIndex+1, endIndex);
            device1.setBitPerSampling(Integer.parseInt((bitsPerSample)));
            // bps for bluetooth is 24 bits
            if(device1.getBitPerSampling()>24) {
                device1.setBitPerSampling(24);
            }
            device1.setSamplingRate(StringUtils.toLong(sampleRate));
            device1.setDescription(desc);

        } catch(Exception ex) {
            Log.e(TAG,"getA2DPCodec",ex);
        }
    }
}
