package apincer.android.tripmate.parser;

import android.content.Context;

import apincer.android.tripmate.extra.PskHttpRequest;
import apincer.android.tripmate.holder.WalkingDetails;
import apincer.android.tripmate.model.WalkingTime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Created by Power on 8/21/2015.
 */
public class WalkingDetailsParser {

    public static boolean connect(Context con, String url)
            throws JSONException, IOException {

        // String result = GetText(con.getResources().openRawResource(
        // R.raw.get_participants));

        String result = "";
        try {
            result = PskHttpRequest.getText(PskHttpRequest
                    .getInputStreamForGetRequest(url));
        } catch (final URISyntaxException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        if (result.length() < 1) {
            return false;
            // Log.e("result is ", "parse " + result);
        }

        //

        WalkingDetails.removeAll();

        final JSONObject detailsObject = new JSONObject(result);

        WalkingTime wTime;

        JSONArray rowArray = detailsObject.getJSONArray("rows");

        for (int i = 0; i < rowArray.length(); i++) {
            final JSONObject eleObject = rowArray.getJSONObject(i);


            final JSONArray eleArray = eleObject.getJSONArray("elements");

            for (int j = 0; j < eleArray.length(); j++) {
                final JSONObject eleeeObject = eleArray.getJSONObject(j);
                JSONObject textD = eleeeObject.getJSONObject("duration");
                wTime = new WalkingTime();

                try {
                    wTime.setTime(textD
                            .getString("text"));

                } catch (Exception e) {
                    // TODO: handle exception
                }

                WalkingDetails.setWalkingTime(wTime);
                wTime = null;

            }
        }


        return true;

    }
}