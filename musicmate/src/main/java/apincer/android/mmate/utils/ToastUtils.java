package apincer.android.mmate.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.service.BroadcastData;
import es.dmoral.toasty.Toasty;

public class ToastUtils {
    public static Typeface currentTypeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    public static int textSize = 12; // in SP
   // public static Toast toastMessage;

    public static void showActionMessage(Context context, ImageView fabCurrentProgress, FloatingActionButton mainAction, int successCount, int errorCount, int pendingTotal, String status, String message) {
        Toast toastMessage = null;
        if(toastMessage!= null) {
            toastMessage.cancel();
        }

        if(Constants.STATUS_SUCCESS.equalsIgnoreCase(status)) {
            toastMessage = Toasty.success(context, message, Toast.LENGTH_SHORT, true);
        }else if(Constants.STATUS_FAIL.equalsIgnoreCase(status)) {
            toastMessage = Toasty.error(context, message, Toast.LENGTH_SHORT, true);
        }else {
            toastMessage = Toasty.info(context, message, Toast.LENGTH_LONG, true);
        }
        if(toastMessage!= null) {
            toastMessage.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 80);
            toastMessage.show();
        }

        if(fabCurrentProgress !=null) {
            int processedCount = successCount + errorCount;
            if (pendingTotal == processedCount) {
                fabCurrentProgress.setVisibility(View.GONE);
                if(mainAction!=null) {
                    mainAction.setVisibility(View.VISIBLE);
                }
            } else {
                int percent = (processedCount*100)/ pendingTotal;
                fabCurrentProgress.setImageBitmap(BitmapHelper.buildArcProgress(context, percent, String.valueOf(pendingTotal-processedCount), false));
                fabCurrentProgress.setVisibility(View.VISIBLE);
                if(mainAction!=null) {
                    mainAction.setVisibility(View.GONE);
                }
            }
        }
    }


    public static void showActionMessage(Context context, String status, String message) {
        Toast toastMessage = null;
        if(toastMessage!= null) {
            toastMessage.cancel();
        }

        if(Constants.STATUS_SUCCESS.equalsIgnoreCase(status)) {
            toastMessage = Toasty.success(context, message, Toast.LENGTH_SHORT, true);
        }else if(Constants.STATUS_FAIL.equalsIgnoreCase(status)) {
            toastMessage = Toasty.error(context, message, Toast.LENGTH_SHORT, true);
        }else {
            toastMessage = Toasty.info(context, message, Toast.LENGTH_LONG, true);
        }
        if(toastMessage!= null) {
            toastMessage.show();
        }
    }


    public static void showBroadcastData(Context context, BroadcastData broadcastData) {
        Toast toastMessage = null;
        if (toastMessage != null) {
            toastMessage.cancel();
        }

        if (BroadcastData.Status.COMPLETED == broadcastData.getStatus()) {
            toastMessage = Toasty.success(context, broadcastData.getMessage(), Toast.LENGTH_SHORT, true);
        } else if (BroadcastData.Status.ERROR == broadcastData.getStatus()) {
            toastMessage = Toasty.error(context, broadcastData.getMessage(), Toast.LENGTH_SHORT, true);
        } else {
            toastMessage = Toasty.info(context, broadcastData.getMessage(), Toast.LENGTH_SHORT, true);
        }
        if (toastMessage != null) {
            toastMessage.show();
        }
    }

    public static void showActionMessageBrowse(Context context, int successCount, int errorCount, int pendingTotal, String status, String message) {
        Toast toastMessage = null;
        if(toastMessage!= null) {
            toastMessage.cancel();
        }

        if(Constants.STATUS_SUCCESS.equalsIgnoreCase(status)) {
            toastMessage = Toasty.success(context, message, Toast.LENGTH_SHORT, true);
        }else if(Constants.STATUS_FAIL.equalsIgnoreCase(status)) {
            toastMessage = Toasty.error(context, message, Toast.LENGTH_SHORT, true);
        }else {
            toastMessage = Toasty.info(context, message, Toast.LENGTH_LONG, true);
        }
        if(toastMessage!= null) {
            toastMessage.show();
        }
/*
        if(mainAction !=null) {
            int processedCount = successCount + errorCount;
            //if (pendingTotal == processedCount) {
            //    mainAction.setVisibility(View.GONE);
               // mainAction.setImageDrawable(ContextCompat.getDrawable(context,R.drawable.ic_center_focus_strong_black_24dp));
               // UIUtils.getTintedDrawable(mainAction.getDrawable(), context.getColor(R.color.now_playing));
           // } else {
                int percent = (processedCount*100)/ pendingTotal;
                mainAction.setImageBitmap(BitmapHelper.buildArcProgress(context, percent, String.valueOf(pendingTotal-processedCount), false));
                mainAction.setVisibility(View.VISIBLE);
           // }
        } */
    }


    public static void showActionMessageEditor(Context context, FloatingActionButton mainAction, int successCount, int errorCount, int pendingTotal, String status, String message) {
        Toast toastMessage = null;
        if(toastMessage!= null) {
            toastMessage.cancel();
        }

        if(Constants.STATUS_SUCCESS.equalsIgnoreCase(status)) {
            toastMessage = Toasty.success(context, message, Toast.LENGTH_SHORT, true);
        }else if(Constants.STATUS_FAIL.equalsIgnoreCase(status)) {
            toastMessage = Toasty.error(context, message, Toast.LENGTH_SHORT, true);
        }else {
            toastMessage = Toasty.info(context, message, Toast.LENGTH_LONG, true);
        }
        if(toastMessage!= null) {
            toastMessage.show();
        }

        if(mainAction !=null) {
            int processedCount = successCount + errorCount;
            if (pendingTotal == processedCount) {
                mainAction.setVisibility(View.VISIBLE);
                mainAction.setImageDrawable(ContextCompat.getDrawable(context,R.drawable.ic_more_vert_black_24dp));
                UIUtils.getTintedDrawable(mainAction.getDrawable(), context.getColor(R.color.now_playing));
            } else {
                int percent = (processedCount*100)/ pendingTotal;
                mainAction.setImageBitmap(BitmapHelper.buildArcProgress(context, percent, String.valueOf(pendingTotal-processedCount), false));
                mainAction.setVisibility(View.VISIBLE);
            }
        }
    }

		/*
        Snacky.builder().setActivity(this)
                .setText("Save Artwork to "+theFilePath.getName())
                .setDuration(Snacky.LENGTH_LONG)
                .setMaxLines(1)
                .success()
                .show(); */
}
