package apincer.android.tripmate.parser;

import java.io.IOException;
import java.net.URISyntaxException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import apincer.android.tripmate.extra.PskHttpRequest;
import apincer.android.tripmate.holder.AllCityReview;
import apincer.android.tripmate.model.ReviewList;

public class CityReviewParser {
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

		AllCityReview.removeAll();

		final JSONObject detailsObject = new JSONObject(result);

		ReviewList reviewList;

		final JSONObject resultObject = detailsObject.getJSONObject("result");

		JSONArray reviewsArray = resultObject.getJSONArray("reviews");

		for (int i = 0; i < reviewsArray.length(); i++) {
			final JSONObject reviewsObject = reviewsArray.getJSONObject(i);

			reviewList = new ReviewList();

			try {
				reviewList.setAuthor_name(reviewsObject
						.getString("author_name"));

			} catch (Exception e) {
				// TODO: handle exception
			}
			try {
				reviewList.setAuthor_text(reviewsObject.getString("text"));

			} catch (Exception e) {
				// TODO: handle exception
			}

			
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

			AllCityReview.setReviewList(reviewList);
			reviewList = null;
			
		}
		return true;

	}
}
