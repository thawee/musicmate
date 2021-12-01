package apincer.android.tripmate.activity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;

import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import apincer.android.tripmate.R;
import apincer.android.tripmate.extra.AllConstants;
import apincer.android.tripmate.extra.PrintLog;
import apincer.android.tripmate.repository.PlaceRepository;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;


public class MainActivity extends Activity implements ConnectionCallbacks,
        OnConnectionFailedListener, LocationListener,View.OnClickListener {

    private Context con;
    private GoogleMap googleMap;

    // LogCat tag
    private static final String TAG = MainActivity.class.getSimpleName();

    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;
    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 1111;

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

    private TextView lblLocation;
    double latitude;
    double longitude;

   
    private LinearLayout atms, banks, bookstores, busstations, cafes, carwash,
            dentist, doctor, food, gasstation, grocery, gym, hospitals,
            temples, theater, park, pharmacy, police, restaurant, school, mall,
            spa, store, university;

    private ShareActionProvider myShareActionProvider;
    LocationFound updateLoc;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(apincer.android.tripmate.R.layout.activity_main);
        getActionBar().setDisplayShowTitleEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(false);
        con = this;
        locUI();
        iUI();
       // enableAd();
        // Check for permission
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, // Activity
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_FINE_LOCATION);
        }

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            Toast.makeText(this, "GPS is Enabled in your devide", Toast.LENGTH_SHORT).show();
        }else{
            showGPSDisabledAlertToUser();
        }
    }

    // Get permission result
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_FINE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted

                } else {
                    // permission was denied
                }
                return;
            }
        }
    }

	/*
    private void enableAd() {
        // TODO Auto-generated method stub

        // adding banner add
//        adView = (AdView) findViewById(R.id.adView);
//        bannerRequest = new AdRequest.Builder().build();
//        adView.loadAd(bannerRequest);

        // adding full screen add
        fullScreenAdd = new InterstitialAd(this);
        fullScreenAdd.setAdUnitId(getString(apincer.android.tripmate.R.string.interstitial_ad_unit_id));
        fullScreenAdRequest = new AdRequest.Builder().build();
        fullScreenAdd.loadAd(fullScreenAdRequest);

        fullScreenAdd.setAdListener(new AdListener() {

            @Override
            public void onAdLoaded() {

                Log.i("FullScreenAdd", "Loaded successfully");
                fullScreenAdd.show();

            }

            @Override
            public void onAdFailedToLoad(int errorCode) {

                Log.i("FullScreenAdd", "failed to Load");
            }
        });

        // TODO Auto-generated method stub

    } */




    private void showGPSDisabledAlertToUser(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage("Turn ON your GPS to get better RESULTS.")
                .setCancelable(false)
                .setPositiveButton("Enable GPS",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent callGPSSettingIntent = new Intent(
                                        android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(callGPSSettingIntent);
                            }
                        });
        alertDialogBuilder.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }
    /**
     * function to load map. If map is not created it will create it for you
     * */
//    private void initilizeMap() {
//
//        if (googleMap == null) {
//            googleMap = ((MapFragment) getFragmentManager().findFragmentById(
//                    R.id.ggmap)).getMap();
//
//            // check if map is created successfully or not
//            if (googleMap == null) {
//                Toast.makeText(getApplicationContext(),
//                        "Sorry! unable to create maps", Toast.LENGTH_SHORT)
//                        .show();
//            }
//        }
//    }




    public void locUI() {
//        lblLocation = (TextView) findViewById(R.id.textViewM);
//        btnShowLocation = (Button) findViewById(R.id.btnShowLocation);
//        btnStartLocationUpdates = (Button)findViewById(R.id.btnLocationUpdates);

        // First we need to check availability of play services
        if (checkPlayServices()) {

            // Building the GoogleApi client
            buildGoogleApiClient();

            createLocationRequest();
        }

//        displayLocation();


        // Show location button click listener
//        btnShowLocation.setOnClickListener(new View.OnClickListener() {
//
//            @Override
//            public void onClick(View v) {
//                displayLocation();
//            }
//        });

        // Toggling the periodic location updates
//        btnStartLocationUpdates.setOnClickListener(new View.OnClickListener() {
//
//            @Override
//            public void onClick(View v) {
//                togglePeriodicLocationUpdates();
//            }
//        });
        // Toggling the periodic location updates
    }





//////// Location //////
    @Override
    public void onStart() {
        super.onStart();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
//        initilizeMap();
        checkPlayServices();

        // Resuming the periodic location updates
        if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLocationUpdates();
    }


    /**
     * Method to display the location on UI
     * */
    public void displayLocation() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLastLocation = LocationServices.FusedLocationApi
                    .getLastLocation(mGoogleApiClient);
        }

        if (mLastLocation != null) {
            latitude = mLastLocation.getLatitude();
            longitude= mLastLocation.getLongitude();
//            lblLocation.setText(latitude + ", " + longitude);

            AllConstants.UPlat=Double.toString(latitude);
            AllConstants.UPlng=Double.toString(longitude);

//            lblLocation.setText(latitude + ", " + longitude);

            PrintLog.myLog("LatLong Found: LATT", +latitude + ", " + longitude);

        } else {

//            lblLocation
//                    .setText("(Couldn't get the location. Make sure location is enabled on the device)");
        }


    }

//    private Menu menu;
//    MenuItem btnStartLocationUpdates = menu.findItem(R.id.action_check_updates);

    /**
     * Method to toggle periodic location updates
     * */
    private void togglePeriodicLocationUpdates() {
        if (!mRequestingLocationUpdates) {
            // Changing the button text

//            btnStartLocationUpdates.setTitle(getString(R.string.btn_stop_location_updates));

            mRequestingLocationUpdates = true;

            // Starting the location updates
            startLocationUpdates();

            Log.d(TAG, "Periodic location updates started!");

        } else {
            // Changing the button text
//            btnStartLocationUpdates
//                    .setTitle(getString(R.string.btn_start_location_updates));

            mRequestingLocationUpdates = false;

            // Stopping the location updates
            stopLocationUpdates();

            Log.d(TAG, "Periodic location updates stopped!");
        }
    }

    /**
     * Creating google api client object
     * */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build();
    }

    /**
     * Creating location request object
     * */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FATEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    /**
     * Method to verify google play services on the device
     * */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Toast.makeText(getApplicationContext(),
                        "This device is not supported.", Toast.LENGTH_LONG)
                        .show();
                finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Starting the location updates
     * */
    protected void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, mLocationRequest, this);
        }
    }

    /**
     * Stopping location updates
     */
    protected void stopLocationUpdates() {
        if(mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }
    }

    /**
     * Google api callback methods
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = "
                + result.getErrorCode());
    }

    @Override
    public void onConnected(Bundle arg0) {

        // Once connected with google api, get the location
        displayLocation();

        if (mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    public void onConnectionSuspended(int arg0) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        // Assign the new location
        mLastLocation = location;

        Toast.makeText(getApplicationContext(), "Location changed!",
                Toast.LENGTH_SHORT).show();

        // Displaying the new location on UI
        displayLocation();
    }



    /////////
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(apincer.android.tripmate.R.menu.menu_main, menu);
        MenuItem item = menu.findItem(apincer.android.tripmate.R.id.menu_item_share);
        myShareActionProvider = (ShareActionProvider) item.getActionProvider();
        myShareActionProvider.setShareHistoryFileName(
                ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);
        myShareActionProvider.setShareIntent(createShareIntent());
        return super.onCreateOptionsMenu(menu);

    }

    //    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.menu, menu);
//        MenuItem item = menu.findItem(R.id.menu_item_share);
//        myShareActionProvider = (ShareActionProvider)item.getActionProvider();
//        myShareActionProvider.setShareHistoryFileName(
//                ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);
//        myShareActionProvider.setShareIntent(createShareIntent());
//        return true;
//    }
//


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Take appropriate action for each action item click
        switch (item.getItemId()) {


            case R.id.idimport:
                // refresh
                importCSV();

                return true;
            case R.id.idexport:
                // refresh
                exportCSV();

                return true;

            case apincer.android.tripmate.R.id.idshow_me:
                // help action

                ShowonMap();
                return true;
/*            case apincer.android.tripmate.R.id.idfind_us:
                // help action

                FindUsActivity();
                return true; */
            case apincer.android.tripmate.R.id.idabout_us:
                // check for updates action
                AboutUs();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void exportCSV() {
        PlaceRepository repository = new PlaceRepository(getApplication());
        repository.exportCSV();
    }

    private void importCSV() {
        PlaceRepository repository = new PlaceRepository(getApplication());
        repository.importCSV();
    }

    /****************
     * Launching new activity
     * */

    private void ShowonMap() {
        Intent i = new Intent(MainActivity.this, MapsActivity.class);
        startActivity(i);
    }
    private void AboutUs() {
        Intent i = new Intent(MainActivity.this, AboutUsActivity.class);
        startActivity(i);
    }
    private void FindUsActivity() {
        Intent i = new Intent(MainActivity.this, FindUsActivity.class);
        startActivity(i);
    }

    private void ListActivity() {
        Intent i = new Intent(this, ListActivity.class);
        startActivity(i);
    }









    private Intent createShareIntent() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                "Find us on Github to share the apk, https://github.com/nsniteshsahni/city-guide-pro/");
        return shareIntent;
    }


    /////////////////Main Menu
    private void iUI() {

        atms = (LinearLayout) findViewById(apincer.android.tripmate.R.id.atms);
        atms.setOnClickListener(this);

        banks = (LinearLayout) findViewById(apincer.android.tripmate.R.id.banks);
        banks.setOnClickListener(this);

        bookstores = (LinearLayout) findViewById(apincer.android.tripmate.R.id.bookstores);
        bookstores.setOnClickListener(this);

        busstations = (LinearLayout) findViewById(apincer.android.tripmate.R.id.busstations);
        busstations.setOnClickListener(this);

        cafes = (LinearLayout) findViewById(apincer.android.tripmate.R.id.cafes);
        cafes.setOnClickListener(this);

        carwash = (LinearLayout) findViewById(apincer.android.tripmate.R.id.carwash);
        carwash.setOnClickListener(this);

        dentist = (LinearLayout) findViewById(apincer.android.tripmate.R.id.dentist);
        dentist.setOnClickListener(this);

        doctor = (LinearLayout) findViewById(apincer.android.tripmate.R.id.doctor);
        doctor.setOnClickListener(this);

        food = (LinearLayout) findViewById(apincer.android.tripmate.R.id.food);
        food.setOnClickListener(this);

        gasstation = (LinearLayout) findViewById(apincer.android.tripmate.R.id.gasstation);
        gasstation.setOnClickListener(this);

        grocery = (LinearLayout) findViewById(apincer.android.tripmate.R.id.grocery);
        grocery.setOnClickListener(this);

        gym = (LinearLayout) findViewById(apincer.android.tripmate.R.id.gym);
        gym.setOnClickListener(this);

        hospitals = (LinearLayout) findViewById(apincer.android.tripmate.R.id.hospitals);
        hospitals.setOnClickListener(this);

        temples = (LinearLayout) findViewById(apincer.android.tripmate.R.id.temples);
        temples.setOnClickListener(this);

        theater = (LinearLayout) findViewById(apincer.android.tripmate.R.id.theater);
        theater.setOnClickListener(this);

        park = (LinearLayout) findViewById(apincer.android.tripmate.R.id.park);
        park.setOnClickListener(this);

        pharmacy = (LinearLayout) findViewById(apincer.android.tripmate.R.id.pharmacy);
        pharmacy.setOnClickListener(this);

        police = (LinearLayout) findViewById(apincer.android.tripmate.R.id.police);
        police.setOnClickListener(this);

        restaurant = (LinearLayout) findViewById(apincer.android.tripmate.R.id.restaurant);
        restaurant.setOnClickListener(this);

        school = (LinearLayout) findViewById(apincer.android.tripmate.R.id.school);
        school.setOnClickListener(this);

        mall = (LinearLayout) findViewById(apincer.android.tripmate.R.id.mall);
        mall.setOnClickListener(this);

        spa = (LinearLayout) findViewById(apincer.android.tripmate.R.id.spa);
        spa.setOnClickListener(this);

        store = (LinearLayout) findViewById(apincer.android.tripmate.R.id.store);
        store.setOnClickListener(this);

        university = (LinearLayout) findViewById(apincer.android.tripmate.R.id.university);
        university.setOnClickListener(this);

    }
    @Override
    public void onClick(View v) {

        togglePeriodicLocationUpdates();

        switch (v.getId()) {

            case apincer.android.tripmate.R.id.atms:
                AllConstants.topTitle = "ATMS LIST";
                AllConstants.query = "atm";
                final Intent atm = new Intent(this, ListActivity.class);
                atm.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(atm);

                break;

            case apincer.android.tripmate.R.id.banks:
                AllConstants.topTitle = "BANKS LIST";
                AllConstants.query = "bank";
                final Intent bank = new Intent(this, ListActivity.class);
                bank.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(bank);

                break;

            case apincer.android.tripmate.R.id.bookstores:
                AllConstants.topTitle = "BOOK STORES LIST";
                AllConstants.query = "book_store";
                final Intent book_store = new Intent(this, ListActivity.class);
                book_store.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(book_store);

                break;
            case apincer.android.tripmate.R.id.busstations:
                AllConstants.topTitle = "BUS STATION LIST";
                AllConstants.query = "bus_station";
                final Intent bus_station = new Intent(this, ListActivity.class);
                bus_station.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(bus_station);

                break;
            case apincer.android.tripmate.R.id.cafes:
                AllConstants.topTitle = "CAFES LIST";
                AllConstants.query = "cafe";
                final Intent cafe = new Intent(this, ListActivity.class);
                cafe.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(cafe);

                break;

            case apincer.android.tripmate.R.id.carwash:
                AllConstants.topTitle = "CAR WASH LIST";
                AllConstants.query = "car_wash";
                final Intent car_wash = new Intent(this, ListActivity.class);
                car_wash.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(car_wash);

                break;

            case apincer.android.tripmate.R.id.dentist:
                AllConstants.topTitle = "DENTIST LIST";
                AllConstants.query = "dentist";
                final Intent dentist = new Intent(this, ListActivity.class);
                dentist.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(dentist);

                break;
            case apincer.android.tripmate.R.id.doctor:
                AllConstants.topTitle = "DOCTOR LIST";
                AllConstants.query = "doctor";
                final Intent doctor = new Intent(this, ListActivity.class);
                doctor.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(doctor);

                break;
            case apincer.android.tripmate.R.id.food:
                AllConstants.topTitle = "FOOD LIST";
                AllConstants.query = "food";
                final Intent food = new Intent(this, ListActivity.class);
                food.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(food);

                break;

            case apincer.android.tripmate.R.id.gasstation:
                AllConstants.topTitle = "GAS STATION LIST";
                AllConstants.query = "gas_station";
                final Intent gas_station = new Intent(this, ListActivity.class);
                gas_station.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(gas_station);

                break;

            case apincer.android.tripmate.R.id.grocery:
                AllConstants.topTitle = "GROCERY LIST";
                AllConstants.query = "grocery_or_supermarket";
                final Intent grocery_or_supermarket = new Intent(this,
                        ListActivity.class);
                grocery_or_supermarket.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(grocery_or_supermarket);

                break;
            case apincer.android.tripmate.R.id.gym:
                AllConstants.topTitle = "GYM LIST";
                AllConstants.query = "gym";
                final Intent gym = new Intent(this, ListActivity.class);
                gym.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(gym);

                break;
            case apincer.android.tripmate.R.id.hospitals:
                AllConstants.topTitle = "HOSPITALS LIST";
                AllConstants.query = "hospital";
                final Intent hospital = new Intent(this, ListActivity.class);
                hospital.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(hospital);

                break;

            case apincer.android.tripmate.R.id.temples:
                AllConstants.topTitle = "TEMPLES LIST";
                AllConstants.query = "hindu_temple";
                final Intent temple = new Intent(this, ListActivity.class);
                temple.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(temple);

                break;

            case apincer.android.tripmate.R.id.theater:
                AllConstants.topTitle = "THEATER LIST";
                AllConstants.query = "movie_theater";
                final Intent movie_theater = new Intent(this, ListActivity.class);
                movie_theater.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(movie_theater);

                break;
            case apincer.android.tripmate.R.id.park:
                AllConstants.topTitle = "PARK LIST";
                AllConstants.query = "rv_park";
                final Intent rv_park = new Intent(this, ListActivity.class);
                rv_park.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(rv_park);

                break;
            case apincer.android.tripmate.R.id.pharmacy:
                AllConstants.topTitle = "PHARMACY LIST";
                AllConstants.query = "pharmacy";
                final Intent pharmacy = new Intent(this, ListActivity.class);
                pharmacy.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(pharmacy);

                break;

            case apincer.android.tripmate.R.id.police:
                AllConstants.topTitle = "POLICE LIST";
                AllConstants.query = "police";
                final Intent police = new Intent(this, ListActivity.class);
                police.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(police);

                break;

            case apincer.android.tripmate.R.id.restaurant:
                AllConstants.topTitle = "RESTAURANT LIST";
                AllConstants.query = "restaurant";
                final Intent restaurant = new Intent(this, ListActivity.class);
                restaurant.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(restaurant);

                break;
            case apincer.android.tripmate.R.id.school:
                AllConstants.topTitle = "SCHOOL LIST";
                AllConstants.query = "school";
                final Intent school = new Intent(this, ListActivity.class);
                school.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(school);

                break;
            case apincer.android.tripmate.R.id.mall:
                AllConstants.topTitle = "SHOPPING MALL LIST";
                AllConstants.query = "shopping_mall";
                final Intent shopping_mall = new Intent(this, ListActivity.class);
                shopping_mall.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(shopping_mall);

                break;

            case apincer.android.tripmate.R.id.spa:
                AllConstants.topTitle = "SPA LIST";
                AllConstants.query = "spa";
                final Intent spa = new Intent(this, ListActivity.class);
                spa.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(spa);

                break;

            case apincer.android.tripmate.R.id.store:
                AllConstants.topTitle = "STORE LIST";
                AllConstants.query = "store";
                final Intent store = new Intent(this, ListActivity.class);
                store.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(store);

                break;

            case apincer.android.tripmate.R.id.university:
                AllConstants.topTitle = "UNIVERSITY LIST";
                AllConstants.query = "university";
                final Intent university = new Intent(this, ListActivity.class);
                university.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(university);

        }
    }
}