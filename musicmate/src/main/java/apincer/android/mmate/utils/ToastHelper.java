package apincer.android.mmate.utils;

import android.app.Activity;

import com.tapadoo.alerter.Alerter;

import apincer.android.mmate.Constants;
import apincer.android.mmate.R;

public class ToastHelper {

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
}
