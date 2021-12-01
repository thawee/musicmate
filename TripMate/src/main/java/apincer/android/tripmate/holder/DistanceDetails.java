package apincer.android.tripmate.holder;

import apincer.android.tripmate.model.DistanceText;

import java.util.Vector;

/**
 * Created by Power on 8/21/2015.
 */
public class DistanceDetails {
    public static Vector<DistanceText> allDistanceText = new Vector<DistanceText>();

    public static Vector<DistanceText> getAllDistanceDetails() {
        return DistanceDetails.allDistanceText;
    }

    public static void setAllDistanceDetails(Vector<DistanceText> allDistanceText) {
        DistanceDetails.allDistanceText = allDistanceText;
    }

    public static DistanceText getDistanceText(int pos) {
        return DistanceDetails.allDistanceText.elementAt(pos);
    }

    public static void setDistanceText(DistanceText DistanceText) {
        DistanceDetails.allDistanceText.addElement(DistanceText);
    }

    public static void removeAll() {
        DistanceDetails.allDistanceText.removeAllElements();
    }
}