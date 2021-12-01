package apincer.android.tripmate.parser;

import android.content.Context;

import apincer.android.tripmate.extra.PskHttpRequest;
import apincer.android.tripmate.holder.DrivingDetails;
import apincer.android.tripmate.model.DrivingTime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Created by Power on 8/19/2015.
 */
public class DrivingDetailsParser {
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

        DrivingDetails.removeAll();

        final JSONObject detailsObject = new JSONObject(result);

        DrivingTime dTime;

        JSONArray rowArray = detailsObject.getJSONArray("rows");

        for (int i = 0; i < rowArray.length(); i++) {
            final JSONObject eleObject = rowArray.getJSONObject(i);


            final JSONArray eleArray = eleObject.getJSONArray("elements");

            for (int j = 0; j < eleArray.length(); j++) {
                final JSONObject eleeeObject = eleArray.getJSONObject(j);
                JSONObject textD = eleeeObject.getJSONObject("duration");
                dTime = new DrivingTime();

                try {
                    dTime.setTime(textD
                            .getString("text"));

                } catch (Exception e) {
                    // TODO: handle exception
                }
                JSONObject textDD = eleeeObject.getJSONObject("distance");

                try {
                    dTime.setDistance(textDD
                            .getString("text"));

                } catch (Exception e) {
                    // TODO: handle exception
                }
                DrivingDetails.setDrivingTime(dTime);
                dTime = null;

            }
        }





//            try {
//                reviewList.setAuthor_text(reviewsObject.getString("text"));
//
//            } catch (Exception e) {
//                // TODO: handle exception
//            }


//			JSONArray aspectsArray = reviewsObject.getJSONArray("aspects");
//			for (int j = 0; j < aspectsArray.length(); j++) {
//				final JSONObject aspectsObject = aspectsArray.getJSONObject(i);
//
//
//				try {
//					reviewList.setAuthor_rating(aspectsObject.getString("rating"));
//
//
//				} catch (Exception e) {
//					// TODO: handle exception
//				}
//
//
//			}




        return true;

    }
}
