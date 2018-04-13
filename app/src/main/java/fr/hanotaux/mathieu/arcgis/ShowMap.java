package fr.hanotaux.mathieu.arcgis;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.IdentifyGraphicsOverlayResult;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.PictureMarkerSymbol;
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters;
import com.esri.arcgisruntime.tasks.geocode.GeocodeResult;
import com.esri.arcgisruntime.tasks.geocode.LocatorTask;
import com.esri.arcgisruntime.tasks.geocode.SuggestResult;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

public class ShowMap extends AppCompatActivity {
    private final String TAG = ShowMap.class.getSimpleName();
    private final String COLUMN_NAME_ADDRESS = "address";
    private final String[] mColumnNames = {BaseColumns._ID, COLUMN_NAME_ADDRESS};
    private SearchView mAddressSearchView;

    private MapView mMapView;
    private LocatorTask mLocatorTask;
    private GraphicsOverlay mGraphicsOverlay;
    private GeocodeParameters mAddressGeocodeParameters;
    private PictureMarkerSymbol mPinSourceSymbol;
    private Callout mCallout;
    private LocationDisplay mLocationDisplay;
    private FusedLocationProviderClient mFusedLocationClient;
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;
    private String distance = "0 km";
    private String durée = "0 min";
    private int prix = 0;
    /**
     * Represents a geographical location.
     */
    protected Location mLastLocation;
    private String mLatitudeLabel;
    private String mLongitudeLabel;
    private int requestCode = 2;
    String[] reqPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission
            .ACCESS_COARSE_LOCATION};


    public static final String ROUTE_UPDATE = "fr.hanotaux.mathieu.arcgis.ROUTE_UPDATE";


    public void sendNotif(View v) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext())
                .setContentTitle("Taxi called")
                .setContentText("A taxi is in the road towards you")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        notificationManager.notify(0, mBuilder.build());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.show_map);

        //ActivityCompat.requestPermissions(ShowMap.this, reqPermissions, requestCode);

        TextView dist = findViewById(R.id.distance);
        dist.setText("0 km");
        TextView duration = findViewById(R.id.duration);
        duration.setText("0 min");
        TextView price = findViewById(R.id.price);
        price.setText("0 €");

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // inflate address search view
        mAddressSearchView = (SearchView) findViewById(R.id.addressSearchView);
        mAddressSearchView.setIconified(false);
        mAddressSearchView.setFocusable(false);
        mAddressSearchView.setQueryHint(getResources().getString(R.string.address_search_hint));

        // define pin drawable
        BitmapDrawable pinDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.pin);
        try {
            mPinSourceSymbol = PictureMarkerSymbol.createAsync(pinDrawable).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "Picture Marker Symbol error: " + e.getMessage());
            Toast.makeText(getApplicationContext(), "Failed to load pin drawable.", Toast.LENGTH_LONG).show();
        }
        // set pin to half of native size
        mPinSourceSymbol.setWidth(19f);
        mPinSourceSymbol.setHeight(72f);

        // create a LocatorTask from an online service
        mLocatorTask = new LocatorTask("http://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer");
        Log.d("locator", String.valueOf(mLocatorTask.getLocatorInfo()));

        // inflate MapView from layout
        mMapView = (MapView) findViewById(R.id.mapView);
        // create a map with the BasemapType topographic
        ArcGISMap map = new ArcGISMap(Basemap.createStreetsVector());
        // set the map to be displayed in this view
        mMapView.setMap(map);

        // set the map viewpoint to start over Pori

        mMapView.setViewpoint(new Viewpoint(61.48333, 21.78333, 1000000));

        // add listener to handle screen taps
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
                identifyGraphic(motionEvent);
                return true;
            }
        });

        // define the graphics overlay
        mGraphicsOverlay = new GraphicsOverlay();

        setupAddressSearchView();
        mLocationDisplay = mMapView.getLocationDisplay();


        getLastLocation();

        IntentFilter inF = new IntentFilter(ROUTE_UPDATE);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(new RouteUpdate(), inF);

    }


    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(ShowMap.this,new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    1);
            return;
        }
        mFusedLocationClient.getLastLocation()
                .addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            mLastLocation = task.getResult();
                            Log.d("Location", "Location" + mLastLocation.getLatitude() + " " + mLastLocation.getLongitude());

                        } else {
                            Log.w(TAG, "getLastLocation:exception", task.getException());
                        }
                    }
                });
    }

    /**
     * Sets up the address SearchView. Uses MatrixCursor to show suggestions to the user as the user inputs text.
     */
    private void setupAddressSearchView() {

        mAddressGeocodeParameters = new GeocodeParameters();
        // get place name and address attributes
        mAddressGeocodeParameters.getResultAttributeNames().add("PlaceName");
        mAddressGeocodeParameters.getResultAttributeNames().add("StAddr");
        // return only the closest result
        mAddressGeocodeParameters.setMaxResults(1);
        mAddressSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String address) {
                // geocode typed address
                geoCodeTypedAddress(address);
                // clear focus from search views
                mAddressSearchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // as long as newText isn't empty, get suggestions from the locatorTask
                if (!newText.equals("")) {
                    final ListenableFuture<List<SuggestResult>> suggestionsFuture = mLocatorTask.suggestAsync(newText);
                    suggestionsFuture.addDoneListener(new Runnable() {

                        @Override
                        public void run() {
                            try {
                                // get the results of the async operation
                                List<SuggestResult> suggestResults = suggestionsFuture.get();
                                MatrixCursor suggestionsCursor = new MatrixCursor(mColumnNames);
                                int key = 0;
                                // add each address suggestion to a new row
                                for (SuggestResult result : suggestResults) {
                                    suggestionsCursor.addRow(new Object[]{key++, result.getLabel()});
                                }
                                // define SimpleCursorAdapter
                                String[] cols = new String[]{COLUMN_NAME_ADDRESS};
                                int[] to = new int[]{R.id.suggestion_address};
                                final SimpleCursorAdapter suggestionAdapter = new SimpleCursorAdapter(ShowMap.this,
                                        R.layout.suggestion, suggestionsCursor, cols, to, 0);
                                mAddressSearchView.setSuggestionsAdapter(suggestionAdapter);
                                // handle an address suggestion being chosen
                                mAddressSearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
                                    @Override
                                    public boolean onSuggestionSelect(int position) {
                                        return false;
                                    }

                                    @Override
                                    public boolean onSuggestionClick(int position) {
                                        // get the selected row
                                        MatrixCursor selectedRow = (MatrixCursor) suggestionAdapter.getItem(position);
                                        // get the row's index
                                        int selectedCursorIndex = selectedRow.getColumnIndex(COLUMN_NAME_ADDRESS);
                                        // get the string from the row at index
                                        String address = selectedRow.getString(selectedCursorIndex);
                                        // use clicked suggestion as query
                                        mAddressSearchView.setQuery(address, true);
                                        return true;
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Geocode suggestion error: " + e.getMessage());
                            }
                        }
                    });
                }
                return true;
            }
        });
    }

    /**
     * Identifies the Graphic at the tapped point.
     *
     * @param motionEvent containing a tapped screen point
     */
    private void identifyGraphic(MotionEvent motionEvent) {
        // get the screen point
        android.graphics.Point screenPoint = new android.graphics.Point(Math.round(motionEvent.getX()),
                Math.round(motionEvent.getY()));
        // from the graphics overlay, get graphics near the tapped location
        final ListenableFuture<IdentifyGraphicsOverlayResult> identifyResultsFuture = mMapView
                .identifyGraphicsOverlayAsync(mGraphicsOverlay, screenPoint, 10, false);
        identifyResultsFuture.addDoneListener(new Runnable() {
            @Override
            public void run() {
                try {
                    IdentifyGraphicsOverlayResult identifyGraphicsOverlayResult = identifyResultsFuture.get();
                    List<Graphic> graphics = identifyGraphicsOverlayResult.getGraphics();
                    // if a graphic has been identified
                    if (graphics.size() > 0) {
                        //get the first graphic identified
                        Graphic identifiedGraphic = graphics.get(0);
                        showCallout(identifiedGraphic);
                    } else {
                        // if no graphic identified
                        mCallout.dismiss();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Identify error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Shows the Graphic's attributes as a Callout.
     *
     * @param graphic containing attributes
     */
    private void showCallout(final Graphic graphic) {
        // create a TextView for the Callout
        TextView calloutContent = new TextView(getApplicationContext());
        calloutContent.setTextColor(Color.BLACK);
        // set the text of the Callout to graphic's attributes
        calloutContent.setText(graphic.getAttributes().get("PlaceName").toString() + "\n"
                + graphic.getAttributes().get("StAddr").toString());
        // get Callout
        mCallout = mMapView.getCallout();
        // set Callout options: animateCallout: true, recenterMap: false, animateRecenter: false
        mCallout.setShowOptions(new Callout.ShowOptions(true, false, false));
        mCallout.setContent(calloutContent);
        // set the leader position and show the callout
        // set the leader position and show the callout
        Point calloutLocation = graphic.computeCalloutLocation(graphic.getGeometry().getExtent().getCenter(), mMapView);
        mCallout.setGeoElement(graphic, calloutLocation);
        mCallout.show();
    }

    /**
     * Geocode an address passed in by the user.
     *
     * @param address read in from searchViews
     */
    private void geoCodeTypedAddress(final String address) {
        // check that address isn't null
        if (address != null) {

            // Execute async task to find the address
            mLocatorTask.addDoneLoadingListener(new Runnable() {
                @Override
                public void run() {
                    if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
                        // Call geocodeAsync passing in an address
                        final ListenableFuture<List<GeocodeResult>> geocodeResultListenableFuture = mLocatorTask
                                .geocodeAsync(address, mAddressGeocodeParameters);
                        geocodeResultListenableFuture.addDoneListener(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // Get the results of the async operation
                                    List<GeocodeResult> geocodeResults = geocodeResultListenableFuture.get();
                                    if (geocodeResults.size() > 0) {
                                        displaySearchResult(geocodeResults.get(0), address);
                                    } else {
                                        Toast.makeText(getApplicationContext(), getString(R.string.location_not_found) + address,
                                                Toast.LENGTH_LONG).show();
                                    }
                                } catch (InterruptedException | ExecutionException e) {
                                    Log.e(TAG, "Geocode error: " + e.getMessage());
                                    Toast.makeText(getApplicationContext(), getString(R.string.geo_locate_error), Toast.LENGTH_LONG)
                                            .show();
                                }
                            }
                        });
                    } else {
                        Log.i(TAG, "Trying to reload locator task");
                        mLocatorTask.retryLoadAsync();
                    }
                }
            });
            mLocatorTask.loadAsync();
        }
    }

    /**
     * Turns a GeocodeResult into a Point and adds it to a GraphicOverlay which is then drawn on the map.
     *
     * @param geocodeResult a single geocode result
     */
    private void displaySearchResult(GeocodeResult geocodeResult, String address) {
        // dismiss any callout
        if (mMapView.getCallout() != null && mMapView.getCallout().isShowing()) {
            mMapView.getCallout().dismiss();
        }
        // clear map of existing graphics
        mMapView.getGraphicsOverlays().clear();
        mGraphicsOverlay.getGraphics().clear();
        // create graphic object for resulting location
        Point resultPoint = geocodeResult.getDisplayLocation();
        Graphic resultLocGraphic = new Graphic(resultPoint, geocodeResult.getAttributes(), mPinSourceSymbol);
        // add graphic to location layer
        mGraphicsOverlay.getGraphics().add(resultLocGraphic);
        // zoom map to result over 3 seconds
        mMapView.setViewpointAsync(new Viewpoint(geocodeResult.getExtent()), 3);
        // set the graphics overlay to the map
        mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
        showCallout(resultLocGraphic);
        getLastLocation();

        // here we'll make the request for the google direction API
        GetRouteService.startActionRoute(getApplicationContext(), String.valueOf(mLastLocation.getLatitude()+","+String.valueOf(mLastLocation.getLongitude())), address.replace(' ', '+'));
    }


    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Location permission was granted. This would have been triggered in response to failing to start the
            // LocationDisplay, so try starting this again.
            Log.d("Test", "HELLOAAZ");
            mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);

            if (!mLocationDisplay.isStarted())
                mLocationDisplay.startAsync();

            Log.d("LOCCONOTHING","");

        } else {
            // If permission was denied, show toast to inform user what was chosen. If LocationDisplay is started again,
            // request permission UX will be shown again, option should be shown to allow never showing the UX again.
            // Alternative would be to disable functionality so request is not shown again.
            Toast.makeText(ShowMap.this, "Denied", Toast
                    .LENGTH_SHORT).show();


        }
    }
    public class RouteUpdate extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (null != intent){
                Log.d("INOK",intent.getAction());
            }
            Log.d(TAG, getRouteFromFile().toString());
            JSONObject obj = getRouteFromFile();
            Log.d(TAG, String.valueOf(obj));

            try {
                JSONArray routes = obj.getJSONArray("routes");
                Log.d(TAG, String.valueOf(routes));
                JSONObject ob0 = routes.getJSONObject(0);
                JSONArray legs = ob0.getJSONArray("legs");
                JSONObject ob1 = legs.getJSONObject(0);
                distance = ob1.getJSONObject("distance").getString("text");
                durée = ob1.getJSONObject("duration").getString("text");
                prix = 10;
                TextView dist = findViewById(R.id.distance);
                dist.setText(distance);
                TextView duration = findViewById(R.id.duration);
                duration.setText(durée);
                TextView price = findViewById(R.id.price);
                Random r = new Random();
                int i1 = r.nextInt(80 - 5) + 5;
                price.setText(i1+" €");
                Log.d(TAG, "UPDATE ROUTE"+distance+durée);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            //musicAdapter.setNewMusic(getMusicFromFile());
        }
    }

    public JSONObject getRouteFromFile(){
        try {
            InputStream is = new FileInputStream(getCacheDir()+"/routes.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return new JSONObject(new String(buffer,"UTF-8"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            return new JSONObject();
        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
        return new JSONObject();
    }




    @Override
    protected void onPause() {
        super.onPause();
        mMapView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.dispose();
    }

}
