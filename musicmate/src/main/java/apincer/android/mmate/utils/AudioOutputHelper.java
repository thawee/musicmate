package apincer.android.mmate.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.os.Build;

import androidx.core.app.ActivityCompat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import apincer.android.mmate.R;
import timber.log.Timber;

public class AudioOutputHelper {
    public static class Device {
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getResId() {
            return resId;
        }

        public void setResId(int resId) {
            this.resId = resId;
        }

        String name;
        String description;

        public String getCodec() {
            return codec;
        }

        public void setCodec(String codec) {
            this.codec = codec;
        }

        String codec = "";

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        String address;
        int resId;
    }

    public interface Callback {
        void onReady(Device device);
    }

    public static void getOutputDevice(Context context, Callback callback) {
        Device outputDevice = new Device();
        MediaRouter mr = (MediaRouter) context.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        MediaRouter.RouteInfo ri = mr.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO);
        // text = text+"\nRoute ; name :" + ri.getName() + " & Desc : "+ri.getDescription()+"& type: " + ri.getSupportedTypes();
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        String srcRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        // String size = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        // text = text+"\nBuffer Size and sample rate; Size :" + size + " & SampleRate: " + rate +" & MusicActive: "+audioManager.isMusicActive();

        String dName = String.valueOf(ri.getName());
        String dType = StringUtils.trimToEmpty(String.valueOf(ri.getDescription()));
        //  String deviceName = "";
        //  String deviceSamplingRate = "";
        AudioDeviceInfo[] adi = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        //  text = text+"\nAudio Devices:";
        for (AudioDeviceInfo a : adi) {
            // final int type = a.getType();
            //   text = text+"\n Audio Device: "+a.getProductName()+" & SampleRate: "+ Arrays.toString(a.getSampleRates()) +" & type: "+a.getType() +" & toString"+a.toString();
            if ("Phone".equalsIgnoreCase(dName)) {
               // String bps = "16";
                int[] c = a.getEncodings();
                String bps = getBPSString(c[0]);
                outputDevice.setName("Android SRC");
                outputDevice.setCodec(Build.MODEL);
                outputDevice.setDescription(getDescription(bps, srcRate));
                outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
                callback.onReady(outputDevice);
                break;
            } else if ("USB".equalsIgnoreCase(dName) || (a.getType() == AudioDeviceInfo.TYPE_USB_DEVICE || a.getType() == AudioDeviceInfo.TYPE_USB_HEADSET)) {
                outputDevice.setName(dName);
                outputDevice.setDescription(Arrays.toString(a.getSampleRates()));
                int[] c = a.getEncodings();
                String bps = getBPSString(c[0]);
                String rate = Arrays.toString(a.getSampleRates()); //a.getSampleRates();
                outputDevice.setDescription(getDescription(bps, rate));

                outputDevice.setResId(R.drawable.ic_baseline_usb_24);
                outputDevice.setAddress(a.getAddress());
                UsbManager usb_manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                HashMap<String, UsbDevice> deviceList = usb_manager.getDeviceList();
                Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

                while (deviceIterator.hasNext()) {
                    UsbDevice device = deviceIterator.next();
                    outputDevice.setName(device.getProductName());
                }
                callback.onReady(outputDevice);
                break;
            } else if (dType.toLowerCase().contains("bluetooth")) {
                // bluetooth
                // BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                outputDevice.setAddress(a.getAddress());
                outputDevice.setResId(R.drawable.ic_round_bluetooth_audio_24);
                getA2DP(context, outputDevice, callback);
            } else {
                // others
                int[] c = a.getEncodings();
                String bps = getBPSString(c[0]);
                outputDevice.setName("Android SRC");
                //String bps = "16";
                outputDevice.setDescription(getDescription(bps, srcRate));
                outputDevice.setResId(R.drawable.ic_baseline_volume_up_24);
                callback.onReady(outputDevice);
                break;
            }
        }
    }

    public static void getA2DP(Context context, Device device, Callback callback) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {

            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                List<BluetoothDevice> devices = proxy.getConnectedDevices();
                BluetoothA2dp bA2dp = (BluetoothA2dp) proxy;
                for (BluetoothDevice dev : devices) {
                    if (device.getAddress().equals(dev.getAddress())) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return;
                        }
                        device.setName(dev.getName());
                        getA2DPCodec(bA2dp, dev, device);
                        callback.onReady(device);
                        break;
                    }
                   // boolean isPlaying = bA2dp.isA2dpPlaying(device);
                    //if(isPlaying) {
                    //    device.getName();
                    //}
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {

            }
        }, BluetoothProfile.A2DP);
    }

    @SuppressLint("PrivateApi")
    private static void getA2DPCodec(BluetoothA2dp bA2dp, BluetoothDevice device, Device device1) {
        device1.setCodec("");
        device1.setDescription("N/A");
        if (bA2dp == null || device == null) return ;
        try {
            Class cls = Class.forName("android.bluetooth.BluetoothA2dp");
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

            device1.setDescription(getDescription(bitsPerSample, sampleRate));

        //    codec = codecName+"("+bitsPerSample+"-"+sampleRate+")"; //object.toString();
        } catch(Exception ex) {
            Timber.e(ex);
        }
    }

    protected static String getBPSString(int audioFormat) {
        switch (audioFormat) {
            case AudioFormat.ENCODING_PCM_8BIT:
                return "8";
            case AudioFormat.ENCODING_PCM_16BIT:
                return "16";
            case AudioFormat.ENCODING_PCM_FLOAT:
                return "24";
            default:
                return Integer.toString(audioFormat);
        }
    }


    private static String getDescription(String bitsPerSample, String sampleRate) {
        String bitString = StringUtils.getFormatedBitsPerSample(Integer.parseInt(bitsPerSample));
        if(sampleRate.startsWith("[")) {
            sampleRate = sampleRate.substring(1);
            if(sampleRate.contains(",")) {
                sampleRate = sampleRate.substring(sampleRate.lastIndexOf(","));
            }
        }
        if(sampleRate.endsWith("]")) {
            sampleRate = sampleRate.substring(0, sampleRate.length()-1);
        }

        String samString = StringUtils.getFormatedAudioSampleRate(StringUtils.toLong(sampleRate), true);
        return bitString+" / "+samString;
    }
}
