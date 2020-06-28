package in.edu.ssn.testssnapp;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Objects;

import in.edu.ssn.testssnapp.models.BusObject;
import in.edu.ssn.testssnapp.services.TransmitLocationService;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import in.edu.ssn.testssnapp.utils.Constants;
import in.edu.ssn.testssnapp.utils.SharedPref;
import in.edu.ssn.testssnapp.utils.YesNoDialogBuilder;
import spencerstudios.com.bungeelib.Bungee;

public class MapActivity extends BaseActivity implements GoogleMap.OnMarkerClickListener, OnMapReadyCallback {
    String routeNo, userId, volunteerId, volunteerBusNo;
    DatabaseReference busLocRef;
    boolean darkMode, isBusVolunteer;
    TextView tvStatusBarHeader, tvNoVolunteer;
    ImageButton cmdStopVolunteering, cmdStartVolunteering;
    volatile boolean isSharingLoc = false;
    LatLng SSNCEPoint = new LatLng(12.7525, 80.196111);
    GoogleMap googleMap;
    MapView busTrackingMap;
    ValueEventListener routeExistslistener, concurrentVolunteerListener;
    ProgressDialog pd;
    boolean isMapLoaded = false;
    BusObject currentBusObject;
    TextView tvVolunteerDetails;
    ImageView backIV, isBusOnlineIV;
    RelativeLayout mapRL, novolunteerRL;

    Thread monitorNetworkStateThread;
    private volatile boolean stopNetworkThread = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (CommonUtils.alerter(this)) {
            startActivity(new Intent(this, NoNetworkActivity.class).putExtra("key", "null"));
            finish();
            Bungee.fade(this);
            return;
        }
        if (darkModeEnabled) {
            setContentView(R.layout.activity_map_dark);
            clearLightStatusBar(this);
        } else {
            setContentView(R.layout.activity_map);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                getWindow().setStatusBarColor(getResources().getColor(R.color.colorAccent));
        }
        pd = new ProgressDialog(this);
        pd.setCancelable(false);
        pd.show();
        initUI(savedInstanceState);

    }

    private void initUI(Bundle bundle) {
        cmdStopVolunteering = findViewById(R.id.stop);
        cmdStartVolunteering = findViewById(R.id.start);
        tvStatusBarHeader = findViewById(R.id.tv_trackbus);
        isBusOnlineIV = findViewById(R.id.iv_busOnlineStatus);
        busTrackingMap = findViewById(R.id.mapView_bus);
        tvVolunteerDetails = findViewById(R.id.tv_volunterid);
        backIV = findViewById(R.id.backIV);
        mapRL = findViewById(R.id.maplayout);
        novolunteerRL = findViewById(R.id.layout_empty);
        tvNoVolunteer = findViewById(R.id.tv_novolunteer);

        routeNo = getIntent().getStringExtra("routeNo");
        tvStatusBarHeader.setText(String.format("Track Bus No. %s", routeNo));

        userId = SharedPref.getString(getApplicationContext(), "email");
        volunteerId = SharedPref.getString(getApplicationContext(), "student_volunteer", userId);
        volunteerBusNo = SharedPref.getString(getApplicationContext(), "volunteer_busno", userId);
        isBusVolunteer = checkVolunteerOfThisBus();
        if (isBusVolunteer)
            tvNoVolunteer.setText(R.string.would_you_like_to_volunteer);

        backIV.setOnClickListener(v -> {
            onBackPressed();
        });

        busLocRef = FirebaseDatabase.getInstance().getReference("Bus Locations").child(routeNo);
        hideMapView();
        initMapView(bundle);
        routeExistslistener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.getChildrenCount() == 4) {
                    if (isMapLoaded) {
                        showMapView();
                        listenForInfoChanges();
                        busLocRef.removeEventListener(this);
                    }
                } else {
                    hideMapView();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                hideMapView();
            }
        };
        busLocRef.addValueEventListener(routeExistslistener);


        //*****************************Volunteer Section ****************************//
        if (isBusVolunteer) {

            if (SharedPref.getBoolean(getApplicationContext(), "stopbutton"))
                if (isMyServiceRunning(TransmitLocationService.class)) disableControls();
                else {
                    Toast.makeText(getApplicationContext(), "Looks like the application crashed last time. Please hit the button to sync yourself with the database!", Toast.LENGTH_LONG).show();
                }
            else
                enableControls();

            concurrentVolunteerListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String s = dataSnapshot.child("currentSharerID").getValue(String.class);
                    Boolean b = dataSnapshot.child("sharingLoc").getValue(Boolean.class);
                    if (isSharingLoc) {
                        if (s != null && !s.equals("null") && !s.equals(userId)) {
                            busLocRef.removeValue();
                            Toast.makeText(getApplicationContext(), "Location input rejected! There's already another volunteer sharing location for this bus!", Toast.LENGTH_LONG).show();
                            stopLocationTransmission();
                        }
                    } else if (b != null && b) {
                        Toast.makeText(getApplicationContext(), "Cannot start location sharing! There's already another volunteer sharing location for this bus!", Toast.LENGTH_LONG).show();
                        stopLocationTransmission();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };


            cmdStartVolunteering.setOnClickListener(v -> {
                if (CommonUtils.alerter(MapActivity.this)) {
                    Toast.makeText(getApplicationContext(), "No network connection!", Toast.LENGTH_SHORT).show();
                    return;
                }
                busLocRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        String s = dataSnapshot.child("currentSharerID").getValue(String.class);
                        if (s != null && !s.equals("null") && !s.equals(userId)) {
                            Toast.makeText(getApplicationContext(), "Location input rejected! There's already another volunteer sharing location for this bus!", Toast.LENGTH_LONG).show();
                        } else
                            checkForLocationPermissionsAndAvailability();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });
            });

            cmdStopVolunteering.setOnClickListener(v -> YesNoDialogBuilder.createDialog(
                    this,
                    darkMode,
                    "Confirm stopping location sharing?",
                    "Your location will stop being shared to the servers.",
                    false,
                    (dialog, which) -> {
                        SharedPref.putBoolean(getApplicationContext(), "stopbutton", false);
                        SharedPref.putString(getApplicationContext(), "routeno", routeNo);
                        stopLocationTransmission();
                        busLocRef.removeValue();
                    },
                    (dialog, which) -> dialog.dismiss()).show());

        } else {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) isBusOnlineIV.getLayoutParams();
            marginParams.setMargins(0, 0, 30, 0);
            cmdStartVolunteering.setVisibility(View.GONE);
            cmdStopVolunteering.setVisibility(View.GONE);
        }
    }

    //***********Location Transmission and to check whether service is running or not ********//

    private void startLocationTransmission() {
        SharedPref.putBoolean(getApplicationContext(), "stopbutton", true);
        SharedPref.putString(getApplicationContext(), "routeno", routeNo);
        busLocRef.addValueEventListener(concurrentVolunteerListener);
        Intent i = new Intent(getApplicationContext(), TransmitLocationService.class);
        i.setAction(TransmitLocationService.ACTION_START_FOREGROUND_SERVICE);
        i.putExtra("routeNo", routeNo);
        startService(i);
        isSharingLoc = true;
        Toast.makeText(getApplicationContext(), "Please wait, it may take some time for the changes to be reflected in the map.", Toast.LENGTH_LONG).show();
        disableControls();
    }

    private void stopLocationTransmission() {
        busLocRef.removeEventListener(concurrentVolunteerListener);
        Intent i = new Intent(getApplicationContext(), TransmitLocationService.class);
        i.setAction(TransmitLocationService.ACTION_STOP_FOREGROUND_SERVICE);
        i.putExtra("routeNo", routeNo);
        i.putExtra("userID", userId);
        startService(i);
        enableControls();
        SharedPref.putString(getApplicationContext(), "routeno", "");
        SharedPref.putBoolean(getApplicationContext(), "stopbutton", false);
        isSharingLoc = false;
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    //*****************************Check whether location permission is granted****************************//

    private void checkForLocationPermissionsAndAvailability() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
            } else
                checkIfLocationIsEnabledAndInitializeTransmission();
        } else
            checkIfLocationIsEnabledAndInitializeTransmission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            checkIfLocationIsEnabledAndInitializeTransmission();
        else {
            Toast.makeText(getApplicationContext(), "Without location Permission App can not start location sharing ", Toast.LENGTH_LONG).show();
        }
    }


    //************************Check whether gps is enabled or not *********************//

    private void checkIfLocationIsEnabledAndInitializeTransmission() {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(LocationServices.API).build();
        googleApiClient.connect();

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(200);
        locationRequest.setFastestInterval(100);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);

        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        //Log.i(TAG "All location settings are satisfied.");
                        startLocationTransmission();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        //Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to upgrade location settings");
                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the result
                            //in onActivityResult();
                            MapActivity.this.startIntentSenderForResult(status.getResolution().getIntentSender(), 1, null, 0, 0, 0, null);
                        } catch (IntentSender.SendIntentException e) {
                            //Log.i(TAG, "PendingIntent unable to execute request.");
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        //Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog not created.");
                        Toast.makeText(getApplicationContext(), "Unable to get location. Cannot proceed further!", Toast.LENGTH_LONG).show();
                        break;
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1)
            if (resultCode != Activity.RESULT_OK)
                Toast.makeText(getApplicationContext(), "Unable to get your location! Cannot start location transmission!", Toast.LENGTH_LONG).show();

            else
                startLocationTransmission();

    }

    //*****enable start stop button controls and check if user is volunteer of the bus****//

    public void enableControls() {
        AlphaAnimation fadeOut = new AlphaAnimation(1, 0), fadeIn = new AlphaAnimation(0, 1);
        fadeOut.setDuration(1000);
        fadeIn.setDuration(1000);
        fadeIn.setStartOffset(500);
        cmdStopVolunteering.startAnimation(fadeOut);
        cmdStopVolunteering.setVisibility(View.GONE);
        cmdStartVolunteering.startAnimation(fadeIn);
        cmdStartVolunteering.setVisibility(View.VISIBLE);

    }

    public void disableControls() {
        AlphaAnimation fadeOut = new AlphaAnimation(1, 0), fadeIn = new AlphaAnimation(0, 1);
        fadeOut.setDuration(1000);
        fadeIn.setDuration(1000);
        fadeIn.setStartOffset(500);
        cmdStartVolunteering.startAnimation(fadeOut);
        cmdStartVolunteering.setVisibility(View.GONE);
        cmdStopVolunteering.startAnimation(fadeIn);
        cmdStopVolunteering.setVisibility(View.VISIBLE);

    }

    public boolean checkVolunteerOfThisBus() {
        if (volunteerId == null || volunteerBusNo == null) {
            return false;
        } else return volunteerId.equals("TRUE") && volunteerBusNo.equals(routeNo);
    }

    //**************************************** Map Section ***************************************//
    @Override
    public boolean onMarkerClick(Marker marker) {
        marker.showInfoWindow();
        return false;
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {

        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(SSNCEPoint, 18f));
        googleMap.setTrafficEnabled(true);
        googleMap.setBuildingsEnabled(true);
        googleMap.setMinZoomPreference(10f);
        googleMap.setMaxZoomPreference(19f);

        if (darkModeEnabled)
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.nightmode_mapstyle));

        googleMap.addMarker(new MarkerOptions().title("College").position(SSNCEPoint));
        googleMap.setOnMarkerClickListener(this);
        this.googleMap = googleMap;
        busTrackingMap.onStart();
        isMapLoaded = true;
        pd.dismiss();

    }

    private void listenForInfoChanges() {
        monitorNetworkStateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    if (!stopNetworkThread) {
                        if (CommonUtils.alerter(MapActivity.this) && currentBusObject != null) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    currentBusObject.setSharerOnline(false);
                                    if (isBusVolunteer && isSharingLoc) stopLocationTransmission();
                                }
                            });
                        }
                    } else Thread.currentThread().interrupt();
                }
            }
        });
        monitorNetworkStateThread.start();
        busLocRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() == 4) {
                    String latLongString = dataSnapshot.child("latLong").getValue(String.class), sharerId = dataSnapshot.child("currentSharerID").getValue(String.class);

                    int speed = dataSnapshot.child("speed").getValue() != null ? (int) (dataSnapshot.child("speed").getValue(int.class) * 3.6 < 1 ? 0 : dataSnapshot.child("speed").getValue(int.class) * 3.6) : 0;
                    boolean isSharingLoc = dataSnapshot.child("sharingLoc").getValue(boolean.class) == null ? false : dataSnapshot.child("sharingLoc").getValue(boolean.class);

                    if (latLongString == null || latLongString.isEmpty() || sharerId == null || sharerId.isEmpty())
                        return;

                    int sep = latLongString.indexOf(',');
                    LatLng currentlatLongs = new LatLng(sep == 1 ? 0 : Double.parseDouble(latLongString.substring(0, sep - 1)), sep == 1 ? 0 : Double.parseDouble(latLongString.substring(sep + 1)));

                    if (currentBusObject == null) {
                        currentBusObject = createBusObject(sharerId, currentlatLongs, speed, isSharingLoc);
                        currentBusObject.setSharerOnline(isSharingLoc);
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentBusObject.getLocation(), 18f));
                    } else if (!sharerId.equals("null")) {
                        if (currentlatLongs.latitude != currentBusObject.getLocation().latitude || currentlatLongs.longitude != currentBusObject.getLocation().longitude)
                            currentBusObject.setLocation(currentlatLongs);
                        currentBusObject.setSpeed(speed);

                        if (!Objects.equals(sharerId, currentBusObject.getCurrentVolunteerId()))
                            currentBusObject.setCurrentVolunteerId(sharerId);

                        if (currentBusObject.isSharerOnline() != isSharingLoc)
                            currentBusObject.setSharerOnline(isSharingLoc);
                    } else currentBusObject.setSharerOnline(false);
                } else if (currentBusObject != null) {
                    currentBusObject.setSharerOnline(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private BusObject createBusObject(String sharerId, LatLng currentlatLongs, int speed, boolean isSharingLoc) {
        Marker busMarker = googleMap.addMarker(new MarkerOptions()
                .position(currentlatLongs)
                .icon(darkModeEnabled ? getBitmapDescriptor(R.drawable.ic_bus_yellow) : getBitmapDescriptor(R.drawable.ic_bus_blue))
                .title("Est. Speed: " + speed + " km/h"));

        return new BusObject(routeNo, sharerId, busMarker, speed, isSharingLoc, new BusObject.OnLocationUpdatedListener() {
            @Override
            public void onSharerIdChanged(String r, String newSharerId) {

            }

            @Override
            public void onLocationChanged(String r, LatLng location) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, googleMap.getCameraPosition().zoom));
            }

            @Override
            public void onOnlineStatusChanged(String r, boolean isOnline) {
                AlphaAnimation fadeOut = new AlphaAnimation(1, 0), fadeIn = new AlphaAnimation(0, 1);
                fadeOut.setDuration(500);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        if (isOnline) {
                            isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_online);
                            tvVolunteerDetails.setText(String.format("Current Volunteer%s", currentBusObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID: " + sharerId + "     "));
                        } else {
                            isBusOnlineIV.setImageResource(R.drawable.circle_busvolunteer_offline);
                            tvVolunteerDetails.setText(String.format("Location last volunteered by%s", currentBusObject.getCurrentVolunteerId().equals(SharedPref.getString(getApplicationContext(), "email")) ? ": You" : " ID: " + currentBusObject.getCurrentVolunteerId() + "     "));
                        }
                        isBusOnlineIV.startAnimation(fadeIn);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }
                });
                fadeIn.setDuration(500);
                fadeIn.setStartOffset(500);
                isBusOnlineIV.startAnimation(fadeOut);

            }

            @Override
            public void onSpeedChanged(String r, int newSpeed) {
                if (currentBusObject.getBusMarker().isInfoWindowShown()) {
                    currentBusObject.getBusMarker().hideInfoWindow();
                    currentBusObject.getBusMarker().showInfoWindow();
                }
            }
        });
    }

    private BitmapDescriptor getBitmapDescriptor(int id) {
        Drawable vectorDrawable = getResources().getDrawable(id);
        int h = vectorDrawable.getIntrinsicHeight();
        int w = vectorDrawable.getIntrinsicWidth();
        vectorDrawable.setBounds(0, 0, w * 2, h * 2);
        Bitmap bm = Bitmap.createBitmap(2 * w, 2 * h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bm);
    }

    private void showMapView() {
        novolunteerRL.setVisibility(View.GONE);
        mapRL.setVisibility(View.VISIBLE);
    }

    private void hideMapView() {
        mapRL.setVisibility(View.GONE);
        novolunteerRL.setVisibility(View.VISIBLE);
        if (isBusVolunteer)
            tvNoVolunteer.setText(R.string.would_you_like_to_volunteer);
    }

    private void initMapView(Bundle b) { //map load
        Bundle mapViewBundle = null;
        if (b != null) {
            mapViewBundle = b.getBundle(Constants.GMAPS_TEST_API_KEY);
        }
        busTrackingMap.onCreate(mapViewBundle);
        busTrackingMap.getMapAsync(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopNetworkThread = true;
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onDestroy();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopNetworkThread = true;
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (monitorNetworkStateThread != null) monitorNetworkStateThread.start();
        if (googleMap != null) busTrackingMap.onResume();
        if (currentBusObject != null)
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentBusObject.getLocation(), googleMap.getCameraPosition().zoom));
    }

    @Override
    public void onPause() {
        super.onPause();
        stopNetworkThread = true;
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (googleMap != null && busTrackingMap != null) busTrackingMap.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle mapViewBundle = outState.getBundle(Constants.GMAPS_TEST_API_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(Constants.GMAPS_TEST_API_KEY, mapViewBundle);
        }
        busTrackingMap.onSaveInstanceState(mapViewBundle);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (googleMap != null) busTrackingMap.onStart();
    }

    @Override
    public void onBackPressed() {
        stopNetworkThread = true;
        Bungee.slideRight(this);
        finish();
    }
}