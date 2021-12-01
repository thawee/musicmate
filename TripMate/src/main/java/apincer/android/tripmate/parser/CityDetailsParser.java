package apincer.android.tripmate.parser;

import java.io.IOException;
import java.net.URISyntaxException;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import apincer.android.tripmate.extra.PrintLog;
import apincer.android.tripmate.extra.PskHttpRequest;
import apincer.android.tripmate.holder.AllCityDetails;
import apincer.android.tripmate.model.CityDetailsList;

public class CityDetailsParser {
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

		AllCityDetails.removeAll();

		final JSONObject detailsObject = new JSONObject(result);

		CityDetailsList detailsData;

		final JSONObject resultObject = detailsObject.getJSONObject("result");

		// JSONArray resultArray = catObject.getJSONArray("results");

		// for (int i = 0; i < resultObject.length(); i++) {
		// final JSONObject resultObjectD = resultObject.getJSONObject(i);

		detailsData = new CityDetailsList();

		try {
			detailsData.setName(resultObject.getString("name"));

		} catch (Exception e) {
			// TODO: handle exception
		}
		try {
			detailsData.setRating(resultObject.getString("rating"));

		} catch (Exception e) {
			// TODO: handle exception
		}
		try {
			detailsData.setIcon(resultObject.getString("icon"));

		} catch (Exception e) {
			// TODO: handle exception
		}
		try {
			detailsData.setFormatted_address(resultObject
					.getString("formatted_address"));

		} catch (Exception e) {
			// TODO: handle exception
		}

		try {
			detailsData.setFormatted_phone_number(resultObject
					.getString("formatted_phone_number"));

		} catch (Exception e) {
			// TODO: handle exception
		}
		try {
			detailsData.setWebsite(resultObject.getString("website"));

		} catch (Exception e) {
			// TODO: handle exception
		}

		final JSONObject resultGeo = resultObject.getJSONObject("geometry");

		final JSONObject location = resultGeo.getJSONObject("location");

		try {
			detailsData.setLat(location.getString("lat"));
			PrintLog.myLog("lat : ", detailsData.getLat() + "");
		} catch (Exception e) {
			// TODO: handle exception
		}

		try {
			detailsData.setLng(location.getString("lng"));
			PrintLog.myLog("lng : ", detailsData.getLng() + "");
		} catch (Exception e) {
			// TODO: handle exception
		}

		
		
		AllCityDetails.setCityDetailsList(detailsData);
		detailsData = null;

		return true;
	}

}
