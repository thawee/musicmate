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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

        int routeType = ri.getDeviceType();
        String routeName = String.valueOf(ri.getName());
        String desc = ri.getDescription() == null ? routeName : String.valueOf(ri.getDescription());
        
        AudioDeviceInfo[] adi = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        AudioDeviceInfo selectedDevice = null;

        // 1. Try to match based on MediaRouter type
        if (routeType == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH) {
            selectedDevice = findAudioDevice(adi, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
            if (selectedDevice != null) {
                setupBluetoothDevice(context, outputDevice, selectedDevice, callback, desc);
                return; // Callback is handled asynchronously
            }
        } else if (routeType == MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER) {
            selectedDevice = findAudioDevice(adi, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        }

        // 2. If no match yet (or MediaRouter returned Unknown/User/Other), check priority devices in AudioDeviceInfo list
        if (selectedDevice == null) {
            // Check for USB/HDMI (High Priority)
            selectedDevice = findAudioDevice(adi, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC);
            if (selectedDevice != null) {
                setupUsbHdmiDevice(context, outputDevice, selectedDevice, desc);
                callback.onReady(outputDevice);
                return;
            }
            
            // Check for Wired Headset/Headphones
            selectedDevice = findAudioDevice(adi, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET);
            if (selectedDevice != null) {
                setupWiredDevice(outputDevice, selectedDevice, desc);
                callback.onReady(outputDevice);
                return;
            }

            // Check for Bluetooth again (fallback)
            selectedDevice = findAudioDevice(adi, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
            if (selectedDevice != null) {
                setupBluetoothDevice(context, outputDevice, selectedDevice, callback, desc);
                return;
            }
        }

        // 3. Fallback to Speaker or whatever we found
        if (selectedDevice == null) {
            selectedDevice = findAudioDevice(adi, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        }

        // 4. Default setup if we have a device, or absolute fallback
        if (selectedDevice != null) {
            setResolutions(outputDevice, selectedDevice, desc);
            outputDevice.setCodec("SRC"); // System Sample Rate Converter
            outputDevice.setName(Build.MODEL);
            outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
        } else {
            outputDevice.setCodec("SRC");
            outputDevice.setName(Build.MODEL);
            outputDevice.setBitPerSampling(16);
            outputDevice.setSamplingRate(48000);
            outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
            outputDevice.setDescription(desc);
        }
        
        callback.onReady(outputDevice);
    }

    private static AudioDeviceInfo findAudioDevice(AudioDeviceInfo[] devices, int... types) {
        for (AudioDeviceInfo device : devices) {
            if (device.isSink()) {
                for (int type : types) {
                    if (device.getType() == type) {
                        return device;
                    }
                }
            }
        }
        return null;
    }

    private static void setupUsbHdmiDevice(Context context, Device outputDevice, AudioDeviceInfo a, String desc) {
        setResolutions(outputDevice, a, desc);
        outputDevice.setResId(R.drawable.ic_baseline_usb_24);
        outputDevice.setAddress(a.getAddress());
        outputDevice.setCodec("Direct");
        
        UsbManager usb_manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usb_manager.getDeviceList();
        
        // Try to find the matching USB device name
        String productName = null;
        for (UsbDevice device : deviceList.values()) {
            // Logic to match specific USB device could be improved here (e.g. by vendor/product ID if available in AudioDeviceInfo address)
            // Current logic takes the first one found, similar to original code.
            productName = device.getProductName();
            break; 
        }
        if (productName != null) {
             outputDevice.setName(productName);
        } else {
             outputDevice.setName(a.getProductName().toString());
        }
    }

    private static void setupWiredDevice(Device outputDevice, AudioDeviceInfo a, String desc) {
        setResolutions(outputDevice, a, desc);
        outputDevice.setCodec("SRC");
        outputDevice.setName(Build.MODEL);
        outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
    }

    private static void setupBluetoothDevice(Context context, Device outputDevice, AudioDeviceInfo a, Callback callback, String desc) {
        outputDevice.setAddress(a.getAddress());
        outputDevice.setResId(R.drawable.ic_round_bluetooth_audio_24);
        // This will call callback.onReady when done
        getA2DP(context, outputDevice, callback, desc);
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
        // Get the highest supported sample rate
        int[] sampleRates = device.getSampleRates();
        int rate = 0;
        for (int sampleRate : sampleRates) {
            if (sampleRate > rate) {
                rate = sampleRate;
            }
        }

        // If no sample rate found, use a default value
        if (rate == 0) {
            rate = 48000; // Default to 48kHz
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
                if (devices != null && !devices.isEmpty()) {
                     for (BluetoothDevice dev : devices) {
                         // Match the address if possible, otherwise take the first one
                         if (device.address == null || device.address.equals(dev.getAddress())) {
                            device.setName(dev.getName());
                            getA2DPCodec(bA2dp, dev, device, desc);
                            callback.onReady(device);
                            break;
                         }
                     }
                } else {
                    // No devices connected, or list empty. Fallback.
                    callback.onReady(device);
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
            Method getCodecStatusMethod = cls.getMethod("getCodecStatus", BluetoothDevice.class);
            Object object = getCodecStatusMethod.invoke(bA2dp, device);
            if (object == null) return ;

            cls = Class.forName("android.bluetooth.BluetoothCodecStatus");
            Method getCodecConfigMethod = cls.getMethod("getCodecConfig");
            Object config = getCodecConfigMethod.invoke(object);
            if (config == null) return ;

            Class<?> configClass = config.getClass();

            // Get Codec Name
            try {
                String codecName = (String) configClass.getMethod("getCodecName").invoke(config);
                device1.setCodec(codecName);
            } catch (Exception e) {
                // Fallback or ignore
            }

            // Get Sample Rate
            try {
                int sampleRateConst = (int) configClass.getMethod("getSampleRate").invoke(config);
                long sampleRate = 0;
                if ((sampleRateConst & 0x1) != 0) sampleRate = 44100;
                else if ((sampleRateConst & 0x2) != 0) sampleRate = 48000;
                else if ((sampleRateConst & 0x4) != 0) sampleRate = 88200;
                else if ((sampleRateConst & 0x8) != 0) sampleRate = 96000;
                else if ((sampleRateConst & 0x10) != 0) sampleRate = 176400;
                else if ((sampleRateConst & 0x20) != 0) sampleRate = 192000;
                
                if (sampleRate > 0) {
                    device1.setSamplingRate(sampleRate);
                }
            } catch (Exception e) {
                 // Fallback or ignore
            }

            // Get Bits Per Sample
            try {
                int bitsPerSampleConst = (int) configClass.getMethod("getBitsPerSample").invoke(config);
                int bitsPerSample = 16;
                if ((bitsPerSampleConst & 0x1) != 0) bitsPerSample = 16;
                else if ((bitsPerSampleConst & 0x2) != 0) bitsPerSample = 24;
                else if ((bitsPerSampleConst & 0x4) != 0) bitsPerSample = 32;

                 device1.setBitPerSampling(bitsPerSample);
            } catch (Exception e) {
                // Fallback or ignore
            }

            device1.setDescription(desc);

        } catch(InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SecurityException) {
                String msg = cause.getMessage();
                if (msg != null && msg.contains("CDM association")) {
                     Log.w(TAG, "Bluetooth codec status access restricted: CDM association required.");
                } else {
                     Log.w(TAG, "Missing permission for getCodecStatus: " + msg);
                }
            } else {
                Log.w(TAG, "Failed to invoke getCodecStatus: " + cause.getMessage());
            }
        } catch(Exception ex) {
            Log.e(TAG,"getA2DPCodec",ex);
        }
    }
}