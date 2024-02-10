package apincer.android.tripmate.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.api.IGeoPoint;
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
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.views.overlay.simplefastpoint.LabelledGeoPoint;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay;
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions;
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme;

import java.util.ArrayList;
import java.util.List;

import apincer.android.tripmate.R;
import apincer.android.tripmate.objectbox.ObjectBox;
import apincer.android.tripmate.objectbox.Place;
import io.objectbox.Box;

public class MapsActivity extends AppCompatActivity {
    Box<Place> placeBox = ObjectBox.get().boxFor(Place.class);
    MapView map;
    MyLocationNewOverlay mLocationOverlay;
    ScaleBarOverlay mScaleBarOverlay;
    CompassOverlay mCompassOverlay;
    FolderOverlay mPlaceOverlay;
    //Map rotation
    private RotationGestureOverlay mRotationGestureOverlay;
    private FloatingActionButton fabMyLocation;
    private TextView placeResultView;

    //private CenterOverlay centerOverlay;
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

        placeResultView = findViewById(R.id.header_results);

       // Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        map.setMultiTouchControls(true);
        map.setHasTransientState(true);
        //scales tiles to the current screen's DPI, helps with readability of labels
        map.setTilesScaledToDpi(true);
        map.setMinZoomLevel(6.5);
        map.setMaxZoomLevel(20.5);
        map.setUseDataConnection (true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        //Whether to display the map data source
        map.getOverlayManager (). getTilesOverlay (). setEnabled (true);

        //The map rotates freely
        mRotationGestureOverlay = new RotationGestureOverlay (map);
        mRotationGestureOverlay.setEnabled (true);
        map.getOverlays (). add (this.mRotationGestureOverlay);

        // default zoom level
        //IMapController mapController = map.getController();
       // mapController.setZoom(12.0);
        //zoom to its bounding box
        map.addOnFirstLayoutListener(new MapView.OnFirstLayoutListener() {

            @Override
            public void onFirstLayout(View v, int left, int top, int right, int bottom) {
                if(map != null && map.getController() != null) {
                    map.getController().zoomTo(9.5);
                   // map.zoomToBoundingBox(sfpo.getBoundingBox(), true);
                }
            }
        });

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

        //centerOverlay = new CenterOverlay();
      //  map.getOverlays().add(centerOverlay);

        //Map Scale bar overlay
        final DisplayMetrics dm = getBaseContext().getResources().getDisplayMetrics();
        mScaleBarOverlay = new ScaleBarOverlay(map);
        mScaleBarOverlay.setCentred(true);
        mScaleBarOverlay.setAlignBottom (true);//Display at the bottom
        mScaleBarOverlay.setScaleBarOffset (dm.widthPixels/3, 160);
        map.getOverlays().add(this.mScaleBarOverlay);

        // Compass
        this.mCompassOverlay = new CompassOverlay(getBaseContext(), new InternalCompassOrientationProvider(getBaseContext()), map);
        this.mCompassOverlay.enableCompass();
        map.getOverlays().add(this.mCompassOverlay);

        //
        fabMyLocation = findViewById(R.id.fab_my_location);
        fabMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                map.getController().animateTo(mLocationOverlay.getMyLocation());
            }
        });

        //display marker from places
        displayPlaces();

        map.invalidate();
    }

    private void displayPlaces() {
        Box<Place> placeBox = ObjectBox.get().boxFor(Place.class);
        List<Place> places = placeBox.getAll();
        mPlaceOverlay = new FolderOverlay();
      /*  for(Place place: places) {
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
        }); */

        //in most cases, there will be no problems of displaying >100k points, feel free to try
        List<IGeoPoint> points = new ArrayList<>();
        for(Place place: places) {
            if(place.getLatitude()!= 0 && place.getLongitude()!=0) {
                points.add(new LabelledGeoPoint(place.getLatitude(), place.getLongitude(),0
                        , place.getName()));
            }
        }

        //wrap them in a theme
        SimplePointTheme pt = new SimplePointTheme(points, true);

        //create label style
        Paint textStyle = new Paint();
        textStyle.setStyle(Paint.Style.FILL);
        textStyle.setColor(Color.parseColor("#0000ff"));
        textStyle.setTextAlign(Paint.Align.CENTER);
        textStyle.setTextSize(24);

        //set some visual options for the overlay
        //we use here MAXIMUM_OPTIMIZATION algorithm, which works well with >100k points
        SimpleFastPointOverlayOptions opt = SimpleFastPointOverlayOptions.getDefaultStyle()
                .setAlgorithm(SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION)
                .setRadius(7).setIsClickable(true).setCellSize(15).setTextStyle(textStyle);

        //create the overlay with the theme
        final SimpleFastPointOverlay sfpo = new SimpleFastPointOverlay(pt, opt);

        //onClick callback
        sfpo.setOnClickListener(new SimpleFastPointOverlay.OnClickListener() {
            @Override
            public void onClick(SimpleFastPointOverlay.PointAdapter points, Integer point) {
                Toast.makeText(map.getContext()
                        , "You clicked " + ((LabelledGeoPoint) points.get(point)).getLabel()
                        , Toast.LENGTH_SHORT).show();
            }
        });

        //add overlay
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                map.getOverlays().add(sfpo);
                map.invalidate();
                placeResultView.setText(points.size()+" places");
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
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
        mLocationOverlay.disableMyLocation();
        mLocationOverlay.disableFollowLocation();
    }

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
                        ListActivity.class);

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
}