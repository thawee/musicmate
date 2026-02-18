package apincer.android.mmate.utils;

import static android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
import static apincer.music.core.utils.StringUtils.isEmpty;

import android.annotation.SuppressLint;
import android.content.Context;
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
import android.media.MediaRouter;
import android.media.AudioProfile;
import android.os.Build;
import android.text.TextPaint;

import androidx.core.content.res.ResourcesCompat;

import java.util.List;

import apincer.android.mmate.R;
import apincer.music.core.playback.spi.MediaTrack;
import apincer.music.core.utils.StringUtils;

public class AudioOutputHelper {
    private static final String TAG = AudioOutputHelper.class.getName();

    public static class Device {
        private boolean bitPerfect;

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

        public String getFriendyDescription() {
            StringBuilder builder = new StringBuilder();
            builder.append(getName());
            if(!StringUtils.isEmpty(getDescription())) {
                builder.append(" [");
                builder.append(getDescription());
                builder.append("]");
            }
            builder.append("\n");
            if(!StringUtils.isEmpty(getCodec())) {
                builder.append(getCodec());
                builder.append(", ");
            }
            if(getSamplingRate()>0) {
                builder.append(StringUtils.formatAudioSampleRate(getSamplingRate(), true));
                builder.append("/");
                builder.append(StringUtils.formatAudioBitsDepth(getBitPerSampling()));
            }
            return builder.toString();
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
            return  bitPerfect;
        }

        public void setBitPerfect(boolean bitPerfect) {
            this.bitPerfect = bitPerfect;
        }
    }

    @SuppressLint("MissingPermission")
    public static Device getOutputDevice(Context context, MediaTrack track) {
        Device outputDevice = new Device();
        MediaRouter mr = (MediaRouter) context.getSystemService(Context.MEDIA_ROUTER_SERVICE);
       // MediaRouter.RouteInfo ri = mr.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

       // int routeType = ri.getDeviceType();
       // String routeName = String.valueOf(ri.getName());
       // String routeDesc = ri.getDescription() == null ? routeName : String.valueOf(ri.getDescription());

        AudioDeviceInfo[] outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        AudioDeviceInfo selectedDevice = getAudioDevice(outputs);

        // 4. Default setup if we have a device, or absolute fallback
        if (selectedDevice != null) {
            readResolutions(outputDevice, selectedDevice);
            outputDevice.setBitPerfect(isBitPerfect(context, selectedDevice, (int) track.getAudioSampleRate()));
            outputDevice.setDescription(typeToString(selectedDevice.getType()));
            outputDevice.setName(String.valueOf(selectedDevice.getProductName()));
            outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
        } else {
            outputDevice.setCodec("SRC");
            outputDevice.setName("Phone Speaker");
            outputDevice.setBitPerSampling(16);
            outputDevice.setSamplingRate(48000);
            outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
            outputDevice.setDescription(Build.MODEL);
        }

        return outputDevice;
    }

    // Check if the current path is truly bit-perfect (Android 14+)
    public static boolean isBitPerfect(Context context, AudioDeviceInfo device, int trackSampleRate) {

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // Get the mixer attributes the system PREFERS for this device
        List<AudioMixerAttributes> mixerAttrs = am.getSupportedMixerAttributes(device);

        for (AudioMixerAttributes attr : mixerAttrs) {
            // BIT_PERFECT mode sends audio 1:1 without volume scaling or mixing
            if (attr.getMixerBehavior() == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT) {
                // Check if the hardware is currently set to your file's sample rate
                if (attr.getFormat().getSampleRate() == trackSampleRate) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AudioDeviceInfo getAudioDevice(AudioDeviceInfo[] devices) {
        AudioDeviceInfo bestDevice = null;
        int highestPriority = -1;

        for (AudioDeviceInfo device : devices) {
            // We only care about outputs (Sinks)
            if (!device.isSink()) continue;

            int type = device.getType();
            int priority = getDevicePriority(type);

            // Logic: Pick the device with the highest audiophile priority
            if (priority > highestPriority) {
                highestPriority = priority;
                bestDevice = device;
            }

            // Log details for debugging (helpful for your audiophile troubleshooting)
           // System.out.println("Checking Device: " + device.getProductName() +
           //         " | Type: " + type +
           //         " | Priority: " + priority);
        }
        return bestDevice;
    }

    private static int getDevicePriority(int type) {
        return switch (type) {
            case AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
                 AudioDeviceInfo.TYPE_USB_ACCESSORY -> 4; // Top priority: USB DACs/High-Res Output
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 3; // Mid priority: Wireless (Convenience)
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 2; // Mid priority: Wireless (Convenience)
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                    1; // Standard Analog Output
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 0; // Fallback: Internal Speakers
            default -> -1;
        };
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
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth audio";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in earphone";
            case AudioDeviceInfo.TYPE_BUILTIN_MIC -> "built-in microphone";
            case TYPE_BUILTIN_SPEAKER -> "Built-in speaker";
            case AudioDeviceInfo.TYPE_BUS -> "BUS";
            case AudioDeviceInfo.TYPE_DOCK -> "DOCK";
            case AudioDeviceInfo.TYPE_FM -> "FM";
            case AudioDeviceInfo.TYPE_FM_TUNER -> "FM tuner";
            case AudioDeviceInfo.TYPE_HDMI -> "HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI audio";
            case AudioDeviceInfo.TYPE_IP -> "IP";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL -> "line digital";
            case AudioDeviceInfo.TYPE_TELEPHONY -> "telephony";
            case AudioDeviceInfo.TYPE_TV_TUNER -> "TV tuner";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory";
            case AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset";
            default -> "";
        };
    }

    private static void readResolutions(Device outputDevice, AudioDeviceInfo device) {
        List<AudioProfile> profiles = device.getAudioProfiles();

        // 1. Get Codec / Format (Requires API 31+)
       /* String codec = "PCM"; // Default for most wired/USB audiophile paths

        if (!profiles.isEmpty()) {
            int format = profiles.get(0).getFormat();
            codec = getFormatName(format);
        }
        outputDevice.setCodec(codec); */

        // 2. Get Bit Depth (Bit Per Sample)
        int[] encodings = device.getEncodings();
        int highestEncoding = getHighestEncoding(encodings);
        int bps = switch (highestEncoding) {
            case AudioFormat.ENCODING_PCM_8BIT -> 8;
            case AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24;
            case AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32;
            default -> 16; // Standard CD quality fallback
        };
        outputDevice.setBitPerSampling(bps);

        // 3. Get the Highest Supported Sample Rate
        int rate = 0;
        // Check Profiles first for accurate hardware capabilities
        for (AudioProfile profile : profiles) {
            for (int sampleRate : profile.getSampleRates()) {
                if (sampleRate > rate) rate = sampleRate;
            }
        }

        // Fallback to basic sample rates if profiles are empty
        if (rate == 0) {
            for (int sampleRate : device.getSampleRates()) {
                if (sampleRate > rate) rate = sampleRate;
            }
        }

        // Final fallback for older devices or internal speakers
        if (rate == 0) rate = 48000;

        outputDevice.setSamplingRate(rate);
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