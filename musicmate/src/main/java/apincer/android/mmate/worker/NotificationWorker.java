package apincer.android.mmate.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class NotificationWorker extends Worker {
public static final String KEY_TITLE = "title";
public static final String KEY_MESSAGE = "message";

           public NotificationWorker(
                @NonNull Context context,
             @NonNull WorkerParameters workerParams) {
                  super(context, workerParams);
             }

               @NonNull
        @Override
      public Result doWork() {
                     String title = getInputData().getString(KEY_TITLE);
                   String message = getInputData().getString(KEY_MESSAGE);

                     // ... use NotificationManagerCompat to create and show the notification

                     return Result.success();
                 }

                 /*
                     1 Data data = new Data.Builder()
    2     .putString(NotificationWorker.KEY_TITLE, "My Notification")
    3     .putString(NotificationWorker.KEY_MESSAGE, "This is a notification from WorkManager.")
    4     .build();
    5
    6 OneTimeWorkRequest notificationWorkRequest =
    7     new OneTimeWorkRequest.Builder(NotificationWorker.class)
    8         .setInputData(data)
    9         .build();
   10
   11 WorkManager.getInstance(getApplicationContext()).enqueue(notificationWorkRequest);
                  */
}
