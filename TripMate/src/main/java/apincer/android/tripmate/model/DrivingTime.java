package apincer.android.tripmate.model;

/**
 * Created by Power on 8/19/2015.
 */
public class DrivingTime {


    String time="";
    String distance="";

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    @Override
    public String toString() {
        return "DrivingTime{" +
                "time='" + time + '\'' +
                ", distance='" + distance + '\'' +
                '}';
    }
}
