package apincer.android.mmate.utils;

import android.app.Activity;
import android.view.View;

import com.tapadoo.alerter.Alerter;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.BroadcastData;

public class ToastHelper {
    //public static Toast toastMessage;

    /*
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
    } */


    public static void showActionMessage(Activity activity, String status, String message) {
        //Toast toastMessage = null;
     /*   if(toastMessage!= null) {
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
        } */

/*
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        snackbar.setTextColor(ContextCompat.getColor(context, R.color.defaultTextColor));
        if(Constants.STATUS_SUCCESS.equalsIgnoreCase(status)) {
            snackbar.setBackgroundTint(ContextCompat.getColor(context, R.color.successColor));
        }else if(Constants.STATUS_FAIL.equalsIgnoreCase(status)) {
            snackbar.setBackgroundTint(ContextCompat.getColor(context,R.color.errorColor));
        }else {
            snackbar.setBackgroundTint(ContextCompat.getColor(context,R.color.infoColor));
        }
        if(view instanceof FloatingActionButton) {
            snackbar.setAnchorView(view);
        }

        snackbar.show(); */

        int bgColor = R.color.infoColor;
        if(Constants.STATUS_SUCCESS.equalsIgnoreCase(status)) {
            bgColor = R.color.successColor;
        }else if(Constants.STATUS_FAIL.equalsIgnoreCase(status)) {
            bgColor = R.color.errorColor;
        }

        Alerter.hide();
        Alerter.create(activity)
                //.setTitle("Alert Title")
                .setText(message)
                .setBackgroundColorRes(bgColor) // or setBackgroundColorInt(Color.CYAN)
                .setEnterAnimation(R.anim.alerter_slide_in_from_left)
                .setExitAnimation(R.anim.alerter_slide_out_to_right)
                .enableSwipeToDismiss()
                .show();
    }


    public static void showBroadcastData(Activity activity, View view, BroadcastData broadcastData) {
        //Toast toastMessage = null;
      /*  if (toastMessage != null) {
            toastMessage.cancel();
        }

        if (BroadcastData.Status.COMPLETED == broadcastData.getStatus()) {
            toastMessage = Toasty.success(context, broadcastData.getMessage(), Toast.LENGTH_SHORT, true);
        } else if (BroadcastData.Status.ERROR == broadcastData.getStatus()) {
            toastMessage = Toasty.error(context, broadcastData.getMessage(), Toast.LENGTH_SHORT, true);
       // } else {
       //     toastMessage = Toasty.info(context, broadcastData.getMessage(), Toast.LENGTH_SHORT, true);
        }
        if (toastMessage != null) {
            toastMessage.show();
        } */
        /*
        Snackbar snackbar = Snackbar.make(view, broadcastData.getMessage(), Snackbar.LENGTH_LONG);
        snackbar.setTextColor(ContextCompat.getColor(context, R.color.defaultTextColor));
        if (BroadcastData.Status.COMPLETED == broadcastData.getStatus()) {
            snackbar.setBackgroundTint(ContextCompat.getColor(context, R.color.successColor));
        } else if (BroadcastData.Status.ERROR == broadcastData.getStatus()) {
            snackbar.setBackgroundTint(ContextCompat.getColor(context,R.color.errorColor));
        }else {
            snackbar.setBackgroundTint(ContextCompat.getColor(context,R.color.infoColor));
        }
        if(view instanceof FloatingActionButton) {
            snackbar.setAnchorView(view);
        }

        snackbar.show(); */

        int bgColor = R.color.infoColor;
        if (BroadcastData.Status.COMPLETED == broadcastData.getStatus()) {
            bgColor = R.color.successColor;
        } else if (BroadcastData.Status.ERROR == broadcastData.getStatus()) {
            bgColor = R.color.errorColor;
        }

        Alerter.hide();
        Alerter.create(activity)
       //.setTitle("Alert Title")
                .setText(broadcastData.getMessage())
                .setBackgroundColorRes(bgColor) // or setBackgroundColorInt(Color.CYAN)
                .setEnterAnimation(R.anim.alerter_slide_in_from_left)
                .setExitAnimation(R.anim.alerter_slide_out_to_right)
                .enableSwipeToDismiss()
                .show();
    }

    /*
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
        } */
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
   // }

/*
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
    } */

		/*
        Snacky.builder().setActivity(this)
                .setText("Save Artwork to "+theFilePath.getName())
                .setDuration(Snacky.LENGTH_LONG)
                .setMaxLines(1)
                .success()
                .show(); */
}
