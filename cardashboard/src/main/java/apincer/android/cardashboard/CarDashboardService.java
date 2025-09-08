package apincer.android.cardashboard;

import android.util.Log;

import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

import org.jspecify.annotations.NonNull;

public class CarDashboardService extends CarAppService {
    // Add a Tag for easy filtering in Logcat
    private static final String TAG = "CarDashboardApp";

    @NonNull
    @Override
    public Session onCreateSession() {

        Log.d(TAG, "onCreateSession called. A new session with the car head unit is being created.");
        try {
            // Your existing code for creating the session goes here
            // If the app crashes after this log, it's somewhere in the code below.
            Log.d(TAG, "Session created successfully. If the crash happens now, it's in the next steps.");
            return new CarDashboardSession();
        } catch (Exception e) {
            // This will catch and log any unhandled exceptions
            Log.e(TAG, "onCreateSession failed with an exception!", e);
            throw e; // Re-throw the exception to let the system handle it
        }
    }

    @Override
    public @NonNull HostValidator createHostValidator() {
        Log.d(TAG, "onCreateHostValidator called. App is being validated by Android Auto.");
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR; //.Builder(this)
                //.addAllowedHosts(HostValidator.ALLOW_ALL_HOSTS_VALIDATOR);

    }
}
