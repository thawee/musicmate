package apincer.android.tripmate.holder;

import apincer.android.tripmate.model.DrivingTime;

import java.util.Vector;

/**
 * Created by Power on 8/19/2015.
 */
public class DrivingDetails {
    public static Vector<DrivingTime> allDrivingData = new Vector<DrivingTime>();

    public static Vector<DrivingTime> getAlldrivingdetails() {
        return DrivingDetails.allDrivingData;
    }

    public static void setAlldrivingdetails(Vector<DrivingTime> allDrivingData) {
        DrivingDetails.allDrivingData = allDrivingData;
    }

    public static DrivingTime getDrivingTime(int pos) {
        return  DrivingDetails.allDrivingData.elementAt(pos);
    }

    public static void setDrivingTime(DrivingTime DrivingTime) {
        DrivingDetails.allDrivingData.addElement(DrivingTime);
    }

    public static void removeAll() {
        DrivingDetails.allDrivingData.removeAllElements();
    }
}
