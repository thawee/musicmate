package apincer.android.tripmate.activity;



import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;


public class SplashScreenActivity extends Activity {
	/** Called when the activity is first created. */
	private final Handler mHandler = new Handler();
	private static final int duration = 3000;

	private Context con;
	// LogCat tag
	private static final String TAG = MainActivity.class.getSimpleName();

	private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;

	private Location mLastLocation;

	// Google client to interact with Google API
	private GoogleApiClient mGoogleApiClient;

	// boolean flag to toggle periodic location updates
	private boolean mRequestingLocationUpdates = false;

	private LocationRequest mLocationRequest;

	// Location updates intervals in sec
	private static int UPDATE_INTERVAL = 10000; // 10 sec
	private static int FATEST_INTERVAL = 5000; // 5 sec
	private static int DISPLACEMENT = 10; // 10 meters

	// UI elements
	private TextView lblLocation;
	private Button btnShowLocation, btnStartLocationUpdates,listButton;
	double latitude;
	double longitude;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(apincer.android.tripmate.R.layout.splashscreen);

		mHandler.postDelayed(mPendingLauncherRunnable,
				SplashScreenActivity.duration);


//		locUI();
		
	}

	@Override
	protected void onPause() {
		super.onPause();
		mHandler.removeCallbacks(mPendingLauncherRunnable);
//		stopLocationUpdates();

	}

	private final Runnable mPendingLauncherRunnable = new Runnable() {

		public void run() {
			//final Intent intent = new Intent(SplashScreenActivity.this,
			//		MainActivity.class);
			final Intent intent = new Intent(SplashScreenActivity.this,
					MapsActivity.class);

			startActivity(intent);
			finish();
		}
	};
	
	////////////////////

//	private void locUI() {
//		lblLocation = (TextView) findViewById(R.id.lblLocation);
//		btnShowLocation = (Button) findViewById(R.id.btnShowLocation);
//		btnStartLocationUpdates = (Button)findViewById(R.id.btnLocationUpdates);
//		listButton = (Button) findViewById(R.id.listBtn);
//		// First we need to check availability of play services
//		if (checkPlayServices()) {
//
//			// Building the GoogleApi client
//			buildGoogleApiClient();
//
//			createLocationRequest();
//		}
//
//		// Show location button click listener
//		btnShowLocation.setOnClickListener(new View.OnClickListener() {
//
//			@Override
//			public void onClick(View v) {
//				displayLocation();
//			}
//		});
//
//		// Toggling the periodic location updates
//		btnStartLocationUpdates.setOnClickListener(new View.OnClickListener() {
//
//			@Override
//			public void onClick(View v) {
//				togglePeriodicLocationUpdates();
//			}
//		});
//		// Toggling the periodic location updates
//		listButton.setOnClickListener(new View.OnClickListener() {
//
//			@Override
//			public void onClick(View v) {
////                AllConstants.topTitle = "BANKS LIST";
////                AllConstants.query = "bank";
//				Intent bank = new Intent(con, MainActivity.class);
//				bank.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//				startActivity(bank);
//			}
//		});
//	}
//
//
//	@Override
//	public void onStart() {
//		super.onStart();
//		if (mGoogleApiClient != null) {
//			mGoogleApiClient.connect();
//		}
//	}
//
//	@Override
//	public void onResume() {
//		super.onResume();
//
//		checkPlayServices();
//
//		// Resuming the periodic location updates
//		if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
//			startLocationUpdates();
//		}
//	}
//
//	@Override
//	public void onStop() {
//		super.onStop();
//		if (mGoogleApiClient.isConnected()) {
//			mGoogleApiClient.disconnect();
//		}
//	}
//
////	@Override
////	public void onPause() {
////
////	}
//
//
//	/**
//	 * Method to display the location on UI
//	 * */
//	public void displayLocation() {
//
//		mLastLocation = LocationServices.FusedLocationApi
//				.getLastLocation(mGoogleApiClient);
//
//		if (mLastLocation != null) {
//			latitude = mLastLocation.getLatitude();
//			longitude= mLastLocation.getLongitude();
//
//
//			AllConstants.UPlat=Double.toString(latitude);
//			AllConstants.UPlng=Double.toString(longitude);
//
//			lblLocation.setText(latitude + ", " + longitude);
//
//			PrintLog.myLog("LatLong Found: LATT", +latitude + ", " + longitude);
//
//		} else {
//
//			lblLocation
//					.setText("(Couldn't get the location. Make sure location is enabled on the device)");
//		}
//
//
//	}
//
//
//
//
//	/**
//	 * Method to toggle periodic location updates
//	 * */
//	private void togglePeriodicLocationUpdates() {
//		if (!mRequestingLocationUpdates) {
//			// Changing the button text
//			btnStartLocationUpdates
//					.setText(getString(R.string.btn_stop_location_updates));
//
//			mRequestingLocationUpdates = true;
//
//			// Starting the location updates
//			startLocationUpdates();
//
//			Log.d(TAG, "Periodic location updates started!");
//
//		} else {
//			// Changing the button text
//			btnStartLocationUpdates
//					.setText(getString(R.string.btn_start_location_updates));
//
//			mRequestingLocationUpdates = false;
//
//			// Stopping the location updates
//			stopLocationUpdates();
//
//			Log.d(TAG, "Periodic location updates stopped!");
//		}
//	}
//
//	/**
//	 * Creating google api client object
//	 * */
//	protected synchronized void buildGoogleApiClient() {
//		mGoogleApiClient = new GoogleApiClient.Builder(this)
//				.addConnectionCallbacks(this)
//				.addOnConnectionFailedListener(this)
//				.addApi(LocationServices.API).build();
//	}
//
//	/**
//	 * Creating location request object
//	 * */
//	protected void createLocationRequest() {
//		mLocationRequest = new LocationRequest();
//		mLocationRequest.setInterval(UPDATE_INTERVAL);
//		mLocationRequest.setFastestInterval(FATEST_INTERVAL);
//		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
//		mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
//	}
//
//	/**
//	 * Method to verify google play services on the device
//	 * */
//	private boolean checkPlayServices() {
//		int resultCode = GooglePlayServicesUtil
//				.isGooglePlayServicesAvailable(this);
//		if (resultCode != ConnectionResult.SUCCESS) {
//			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
//				GooglePlayServicesUtil.getErrorDialog(resultCode, this,
//						PLAY_SERVICES_RESOLUTION_REQUEST).show();
//			} else {
//				Toast.makeText(getApplicationContext(),
//						"This device is not supported.", Toast.LENGTH_LONG)
//						.show();
//				finish();
//			}
//			return false;
//		}
//		return true;
//	}
//
//	/**
//	 * Starting the location updates
//	 * */
//	protected void startLocationUpdates() {
//
//		LocationServices.FusedLocationApi.requestLocationUpdates(
//				mGoogleApiClient, mLocationRequest, this);
//
//	}
//
//	/**
//	 * Stopping location updates
//	 */
//	protected void stopLocationUpdates() {
//		LocationServices.FusedLocationApi.removeLocationUpdates(
//				mGoogleApiClient, this);
//	}
//
//	/**
//	 * Google api callback methods
//	 */
//	@Override
//	public void onConnectionFailed(ConnectionResult result) {
//		Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = "
//				+ result.getErrorCode());
//	}
//
//	@Override
//	public void onConnected(Bundle arg0) {
//
//		// Once connected with google api, get the location
//		displayLocation();
//
//		if (mRequestingLocationUpdates) {
//			startLocationUpdates();
//		}
//	}
//
//	@Override
//	public void onConnectionSuspended(int arg0) {
//		mGoogleApiClient.connect();
//	}
//
//	@Override
//	public void onLocationChanged(Location location) {
//		// Assign the new location
//		mLastLocation = location;
//
//		Toast.makeText(getApplicationContext(), "Location changed!",
//				Toast.LENGTH_SHORT).show();
//
//		// Displaying the new location on UI
//		displayLocation();
//	}
//


}
