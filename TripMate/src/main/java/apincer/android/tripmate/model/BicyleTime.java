package apincer.android.tripmate.model;

/**
 * Created by Power on 8/21/2015.
 */
public class BicyleTime {

    String time="";

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    @Override
    public String toString() {
        return "BicyleTime{" +
                "time='" + time + '\'' +
                '}';
    }
}