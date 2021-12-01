package apincer.android.tripmate.holder;

import apincer.android.tripmate.model.WalkingTime;

import java.util.Vector;

/**
 * Created by Power on 8/21/2015.
 */
public class WalkingDetails {
    public static Vector<WalkingTime> allWalkingData = new Vector<WalkingTime>();

    public static Vector<WalkingTime> getAllWalkingDetails() {
        return WalkingDetails.allWalkingData;
    }

    public static void setAllWalkingDetails(Vector<WalkingTime> allWalkingData) {
        WalkingDetails.allWalkingData = allWalkingData;
    }

    public static WalkingTime getWalkingTime(int pos) {
        return  WalkingDetails.allWalkingData.elementAt(pos);
    }

    public static void setWalkingTime(WalkingTime WalkingTime) {
        WalkingDetails.allWalkingData.addElement(WalkingTime);
    }

    public static void removeAll() {
        WalkingDetails.allWalkingData.removeAllElements();
    }
}