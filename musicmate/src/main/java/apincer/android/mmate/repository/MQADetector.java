package apincer.android.mmate.repository;

import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.toLong;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.util.Log;

import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mqaidentifier.NativeLib;

public class MQADetector {
    private static final String TAG = MQADetector.class.getName();

    public static void detectMQA(MusicTag tag, long millis) {
        final Object lock = new Object();
        new Thread(() -> {
            detectMQA(tag);
            synchronized (lock) {
                lock.notify();
            }
        }).start();
        synchronized (lock) {
            try {
                // Wait for specific millis and release the lock.
                // If blockingMethod is done during waiting time, it will wake
                // me up and give me the lock, and I will finish directly.
                // Otherwise, when the waiting time is over and the
                // blockingMethod is still
                // running, I will reacquire the lock and finish.
                lock.wait(millis);
            } catch (InterruptedException e) {
                Log.e(TAG, "detectMQA", e);
            }
        }
    }

    public static void detectMQA(MusicTag tag) {
        if(!MusicTagUtils.isFLACFile(tag)) return; // scan only flac
        if(tag.isMqaScanned()) return; //prevent re scan
        try {
            NativeLib lib = new NativeLib();
            String mqaInfo = StringUtils.trimToEmpty(lib.getMQAInfo(tag.getPath()));
            // MQA Studio|96000
            // MQA|96000
            if(!isEmpty(mqaInfo) && mqaInfo.contains("|")) {
                String[] tags = mqaInfo.split("\\|", -1);
                tag.setMqaInd(trimToEmpty(tags[0]));
                tag.setMqaSampleRate(toLong(tags[1]));
                tag.setMqaScanned(true);
            }else {
                tag.setMqaInd("None");
                tag.setMqaScanned(true);
            }
        }catch (Exception ex) {
            tag.setMqaInd("None");
            tag.setMqaScanned(true);
            Log.e(TAG, "detectMQA", ex);
        }
    }

}
