package apincer.android.tripmate.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.api.IMapView;
import org.osmdroid.bonuspack.kml.KmlDocument;
import org.osmdroid.bonuspack.kml.KmlFeature;
import org.osmdroid.bonuspack.kml.KmlFolder;
import org.osmdroid.bonuspack.kml.KmlPlacemark;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.ItemizedOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.List;

import apincer.android.tripmate.BuildConfig;
import apincer.android.tripmate.R;
import apincer.android.tripmate.objectbox.ObjectBox;
import apincer.android.tripmate.objectbox.Place;
import io.objectbox.Box;

public class MapsActivity extends AppCompatActivity { //implements LocationListener {
    Box<Place> placeBox = ObjectBox.get().boxFor(Place.class);
   // GoogleMap googleMap;
    MapView map;
    MyLocationNewOverlay mLocationOverlay;
    ScaleBarOverlay mScaleBarOverlay;
    CompassOverlay mCompassOverlay;
    FolderOverlay mPlaceOverlay;

    private CenterOverlay centerOverlay;
    private Paint circlePaint;

    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 1111;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
//        getActionBar().setDisplayShowTitleEnabled(true);
//        getActionBar().setDisplayHomeAsUpEnabled(false);
//        getActionBar().setTitle("My Place");

        // Check for permission
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, // Activity
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_FINE_LOCATION);
        }

        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        map.setMultiTouchControls(true);
        map.setHasTransientState(true);
        //scales tiles to the current screen's DPI, helps with readability of labels
        map.setTilesScaledToDpi(true);
        map.setMinZoomLevel(6.5);
        map.setMaxZoomLevel(20.5);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        // default zoom level
        IMapController mapController = map.getController();
        mapController.setZoom(12.0);

        circlePaint = new Paint();
        circlePaint.setARGB(0, 255, 100, 100);
        circlePaint.setAntiAlias(true);

        // my location
        this.mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(getBaseContext()),map);
        this.mLocationOverlay.enableMyLocation();
        this.mLocationOverlay.enableFollowLocation();
        this.mLocationOverlay.setDrawAccuracyEnabled(true);
        map.getOverlays().add(this.mLocationOverlay);
        this.mLocationOverlay.runOnFirstFix(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        map.getController().animateTo(mLocationOverlay.getMyLocation());
                    }
                });
            }
        });

        centerOverlay = new CenterOverlay();
        map.getOverlays().add(centerOverlay);

        //Map Scale bar overlay
        final DisplayMetrics dm = getBaseContext().getResources().getDisplayMetrics();
        mScaleBarOverlay = new ScaleBarOverlay(map);
        mScaleBarOverlay.setCentred(true);
//play around with these values to get the location on screen in the right place for your application
        mScaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 10);
        map.getOverlays().add(this.mScaleBarOverlay);

        // Compass
        this.mCompassOverlay = new CompassOverlay(getBaseContext(), new InternalCompassOrientationProvider(getBaseContext()), map);
        this.mCompassOverlay.enableCompass();
        map.getOverlays().add(this.mCompassOverlay);

        //display marker from places
        displayPlaces();

        map.invalidate();

        // Getting Google Play availability status
      /*  int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getBaseContext());

        // Showing status
        if(status!= ConnectionResult.SUCCESS){ // Google Play Services are not available

            int requestCode = 10;
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, this, requestCode);
            dialog.show();

        }else { // Google Play Services are available

            // Getting reference to the SupportMapFragment of activity_main.xml
            SupportMapFragment fm = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

            // Getting GoogleMap object from the fragment
            //googleMap = fm.getMap();
			fm.getMapAsync(this); */
/*
            // Enabling MyLocation Layer of Google Map
            googleMap.setMyLocationEnabled(true);

            // Getting LocationManager object from System Service LOCATION_SERVICE
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            // Creating a criteria object to retrieve provider
            Criteria criteria = new Criteria();

            // Getting the name of the best provider
            String provider = locationManager.getBestProvider(criteria, true);

            // Getting Current Location
            Location location = locationManager.getLastKnownLocation(provider);

            if(location!=null){
                onLocationChanged(location);
            }
            locationManager.requestLocationUpdates(provider, 20000, 0, this);
			*/
       // }
    }

    private void displayPlaces() {
        Box<Place> placeBox = ObjectBox.get().boxFor(Place.class);
        List<Place> places = placeBox.getAll();
        mPlaceOverlay = new FolderOverlay();
        for(Place place: places) {
            if(place.getLatitude()!= 0 && place.getLongitude()!=0) {
                Marker marker = new Marker(map);
                marker.setPosition(new GeoPoint(place.getLatitude(), place.getLongitude()));
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                marker.setTitle(place.getName());
                marker.setSubDescription(place.getDescription());
                mPlaceOverlay.add(marker);
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                map.getOverlays().add(mPlaceOverlay);
                map.invalidate();
            }
        });
    }


    // Get permission result
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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

    @Override
    public void onResume() {
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation();
    }

    @Override
    public void onPause() {
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
        mLocationOverlay.disableMyLocation();
        mLocationOverlay.disableFollowLocation();
    }

	/*
    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
		// Enabling MyLocation Layer of Google Map
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            googleMap.setMyLocationEnabled(true);

            // Getting LocationManager object from System Service LOCATION_SERVICE
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            // Creating a criteria object to retrieve provider
            Criteria criteria = new Criteria();

            // Getting the name of the best provider
            String provider = locationManager.getBestProvider(criteria, true);

            // Getting Current Location
            Location location = locationManager.getLastKnownLocation(provider);

            if (location != null) {
                onLocationChanged(location);
            }
            locationManager.requestLocationUpdates(provider, 20000, 0, this);
        }
    } */

    /*
    @Override
    public void onLocationChanged(Location location) {

        // Getting latitude of the current location
        double latitude = location.getLatitude();

        // Getting longitude of the current location
        double longitude = location.getLongitude();

        IMapController mapController = map.getController();
       // mapController.setZoom(9.5);
        GeoPoint startPoint = new GeoPoint(latitude, longitude);
        mapController.setCenter(startPoint);
       // map.setExpectedCenter(startPoint); */

        /*
        // Creating a LatLng object for the current location
        LatLng latLng = new LatLng(latitude, longitude);

        // Showing the current location in Google Map
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
//        googleMap.addMarker(new MarkerOptions()
//                .position(latLng)
//                .title("Golden Gate Bridge")
//                .snippet("San Francisco")
//                .icon(BitmapDescriptorFactory
//                        .fromResource(R.drawable.mappin)));
        // Zoom in the Google Map
        googleMap.animateCamera(CameraUpdateFactory.zoomTo(15));
*/

        // Setting latitude and longitude in the TextView tv_location


   // }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.testmenu, menu);
//        return true;
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(apincer.android.tripmate.R.menu.menu_maps, menu);
      /*  MenuItem item = menu.findItem(apincer.android.tripmate.R.id.menu_item_share);
        myShareActionProvider = (ShareActionProvider) item.getActionProvider();
        myShareActionProvider.setShareHistoryFileName(
                ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);
        myShareActionProvider.setShareIntent(createShareIntent());
        */
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.id_list_places:
                // goto List
                final Intent intent = new Intent(MapsActivity.this,
                        MainActivity.class);

                startActivity(intent);

                return true;
            case R.id.id_add_place:
                loadKmLs();


                return true;
            case android.R.id.home:
                this.finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    private void loadKmLs() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                KmlDocument kmlDoc = new KmlDocument();
                String campCarThailand = "https://www.google.com/maps/d/kml?forcekml=1&mid=1rhNqCUE-iLDwcxtRIrKsXa-jMqVgSXIs";
                kmlDoc.parseKMLUrl(campCarThailand);
                KmlFolder kmlFolder = kmlDoc.mKmlRoot;
                parseKmlFolder(kmlFolder, campCarThailand);
                displayPlaces();
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private void parseKmlFolder(KmlFolder kmlFolder,String url) {
        for(KmlFeature feature: kmlFolder.mItems) {
            if(feature instanceof KmlFolder) {
                //
                parseKmlFolder((KmlFolder) feature, url);
            }else if(feature instanceof KmlPlacemark) {
                Place place = new Place();
                GeoPoint point = ((KmlPlacemark) feature).mGeometry.mCoordinates.get(0);
                place.setLatitude(point.getLatitude());
                place.setLongitude(point.getLongitude());
                place.setDescription(feature.mDescription);
                place.setName(feature.mName);
                place.setSourceName(kmlFolder.mName);
                place.setSourceUrl(url);
                place.setType(feature.mStyle);
                placeBox.put(place);
            }
        }
    }

    private class CenterOverlay extends ItemizedOverlay<OverlayItem> {

        public CenterOverlay() {
            super(ContextCompat.getDrawable(getBaseContext(), R.drawable.actionbar_bg));
        }

        public void update() {
            populate();
        }

        @Override
        protected OverlayItem createItem(int i) {
            return new OverlayItem(null, null, map.getMapCenter());
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean onSnapToItem(int x, int y, Point snapPoint, IMapView mapView) {
            return false;
        }
    }
}