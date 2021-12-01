package apincer.android.tripmate.extra;

public class AllURL {

	/***
	 * 
	 * Login URL
	 */
	public static String loginURL(String email, String password) {
		return BaseURL.HTTP + "login.php?EmailAddress=" + email + "&Password="
				+ password;
	}

	/***
	 * 
	 * View NearBy List
	 */

	public static String nearByURL(String UPLat, String UPLng, String query,
			String apiKey) {
		return BaseURL.HTTP + "nearbysearch/json?location=" + UPLat + ","
				+ UPLng + "&rankby=distance&types=" + query
				+ "&sensor=false&key=" + apiKey;
	}

	/***
	 * 
	 * View CityGuide Details
	 */

	public static String cityGuideDetailsURL(String reference, String apiKey) {
		return BaseURL.HTTP + "details/json?reference=" + reference
				+ "&sensor=true&key=" + apiKey;
	}

	public static String bicyleURL(String UPLat, String UPLng,String DLat, String DLng, String apiKey) {
		return "https://maps.googleapis.com/maps/api/distancematrix/json?origins="+ UPLat + ","
				+ UPLng +"&destinations="+ DLat + ","
				+ DLng +"&mode=bicycling&language=en&"+apiKey;
	}
	public static String drivingURL(String UPLat, String UPLng,String DLat, String DLng, String apiKey) {
		return "https://maps.googleapis.com/maps/api/distancematrix/json?origins="+ UPLat + ","
				+ UPLng +"&destinations="+ DLat + ","
				+ DLng +"&mode=DRIVING&language=en&"+apiKey;
	}
	public static String walkURL(String UPLat, String UPLng,String DLat, String DLng, String apiKey) {
		return "https://maps.googleapis.com/maps/api/distancematrix/json?origins="+ UPLat + ","
				+ UPLng +"&destinations="+ DLat + ","
				+ DLng +"&mode=walking&language=en&"+apiKey;
	}

	public static String bicycleURL(String UPLat, String UPLng,String DLat, String DLng, String apiKey) {
		return "https://maps.googleapis.com/maps/api/distancematrix/json?origins="+ UPLat + ","
				+ UPLng +"&destinations="+ DLat + ","
				+ DLng +"&mode=bicycling&language=en&"+apiKey;
	}
	public static String driveURL(String UPLat, String UPLng,String DLat, String DLng, String apiKey) {
		return "https://maps.googleapis.com/maps/api/distancematrix/json?origins="+ UPLat + ","
				+ UPLng +"&destinations="+ DLat + ","
				+ DLng +"&mode=driving&language=en&"+apiKey;
	}



}
